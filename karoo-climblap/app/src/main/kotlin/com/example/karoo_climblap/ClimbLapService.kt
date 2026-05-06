package com.example.karoo_climblap

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.MarkLap
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext

private const val TAG = "ClimbLapService"

/**
 * ClimbLapService uses Karoo's NavigationState to get the list of climbs on the route,
 * then tracks the rider's position along the route via DISTANCE_TO_DESTINATION streaming
 * to detect when they enter and exit each climb, firing MarkLap at each transition.
 *
 * HOW IT WORKS
 * ─────────────
 * 1. Subscribe to OnNavigationState.Params events.
 *    - NavigatingRoute gives us a List<Climb> and routeDistance (total metres).
 *    - NavigatingToDestination also gives climbs but no total distance, so we skip it.
 *    - Idle means no route — we stay dormant.
 *
 * 2. Subscribe to DataType.Type.DISTANCE_TO_DESTINATION stream (~1Hz).
 *    - distanceAlongRoute = routeDistance - distanceToDestination (clamped ≥ 0)
 *    - We check which climb window (if any) contains distanceAlongRoute.
 *
 * 3. On transition:
 *    - null → climb   : fire start lap
 *    - climb → null   : fire end lap
 *    - climb A → B    : fire end lap for A, then start lap for B (no debounce between the pair)
 *
 * 4. Whenever routeClimbs changes (reroute, new route), reset activeClimbIndex so stale
 *    index references into the old list can't cause phantom laps.
 *
 * ROUTE REQUIRED
 * ──────────────
 * Only NavigatingRoute is supported. NavigatingToDestination doesn't expose a total
 * route distance so we can't convert distanceToDestination into distanceAlongRoute.
 */
class ClimbLapService(
    private val karooSystem: KarooSystemService,
    private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("climb_lap_prefs", Context.MODE_PRIVATE)

    private val debounceMs: Long get() = prefs.getInt("debounce_seconds", 10) * 1000L

    // ── Route state (all access serialised onto climbThread) ──────────────────
    private var routeDistance: Double = 0.0
    private var routeClimbs: List<OnNavigationState.NavigationState.Climb> = emptyList()
    private var activeClimbIndex: Int = -1
    private var lastLapAt = 0L

    // ── Threading ─────────────────────────────────────────────────────────────
    @Suppress("EXPERIMENTAL_API_USAGE")
    private val climbThread = newSingleThreadContext("ClimbDetector")
    private val scope = CoroutineScope(climbThread + SupervisorJob())

    // ── Consumer IDs ──────────────────────────────────────────────────────────
    private var rideStateConsumerId: String? = null
    private var navigationConsumerId: String? = null
    private var distanceConsumerId: String? = null

    // ── Public API ────────────────────────────────────────────────────────────

    fun start() {
        Log.i(TAG, "Starting ClimbLapService")
        listenToRideState()
    }

    fun stop() {
        Log.i(TAG, "Stopping ClimbLapService")
        rideStateConsumerId?.let { karooSystem.removeConsumer(it) }
        stopStreams()
        scope.cancel()
        climbThread.close()
    }

    // ── Ride state ────────────────────────────────────────────────────────────

    private fun listenToRideState() {
        rideStateConsumerId = karooSystem.addConsumer { rideState: RideState ->
            when (rideState) {
                is RideState.Recording -> {
                    Log.i(TAG, "Ride started")
                    startStreams()
                }
                is RideState.Paused -> stopStreams()
                is RideState.Idle -> {
                    Log.i(TAG, "Ride ended")
                    stopStreams()
                    scope.launch { resetState() }
                }
            }
        }
    }

    // ── Stream management ─────────────────────────────────────────────────────

    private fun startStreams() {
        startNavigationStream()
        startDistanceStream()
    }

    private fun stopStreams() {
        navigationConsumerId?.let { karooSystem.removeConsumer(it); navigationConsumerId = null }
        distanceConsumerId?.let { karooSystem.removeConsumer(it); distanceConsumerId = null }
    }

    private fun startNavigationStream() {
        if (navigationConsumerId != null) return
        navigationConsumerId = karooSystem.addConsumer(
            OnNavigationState.Params
        ) { event: OnNavigationState ->
            scope.launch { onNavigationState(event.state) }
        }
    }

    private fun startDistanceStream() {
        if (distanceConsumerId != null) return
        distanceConsumerId = karooSystem.addConsumer(
            OnStreamState.StartStreaming(DataType.Type.DISTANCE_TO_DESTINATION)
        ) { event: OnStreamState ->
            val streamState = event.state
            if (streamState is StreamState.Streaming) {
                // Use the named field for this data type (DataType.Field.DISTANCE_TO_DESTINATION)
                // rather than the generic SINGLE field, for clarity and correctness.
                val distToDestination = streamState.dataPoint.values[DataType.Field.DISTANCE_TO_DESTINATION]
                    ?: streamState.dataPoint.values[DataType.Field.SINGLE] // fallback
                if (distToDestination != null) {
                    scope.launch { onDistanceUpdate(distToDestination) }
                }
            }
        }
    }

    // ── Navigation state ──────────────────────────────────────────────────────

    private fun onNavigationState(state: OnNavigationState.NavigationState) {
        when (state) {
            is OnNavigationState.NavigationState.NavigatingRoute -> {
                val newClimbs = state.climbs.sortedBy { it.startDistance }
                // Fix #7: reset activeClimbIndex whenever the climb list changes
                // (covers initial load, reroutes, and route changes mid-ride)
                if (newClimbs != routeClimbs) {
                    Log.i(TAG, "Route/climbs changed — resetting active climb index")
                    activeClimbIndex = -1
                }
                routeDistance = state.routeDistance
                routeClimbs = newClimbs
                Log.i(TAG, "Route loaded: ${routeClimbs.size} climbs, distance=${routeDistance}m")
                routeClimbs.forEachIndexed { i, c ->
                    Log.d(TAG, "  Climb $i: start=${c.startDistance}m length=${c.length}m grade=${c.grade}%")
                }
            }
            is OnNavigationState.NavigationState.NavigatingToDestination -> {
                // Fix #1: NavigatingToDestination has no routeDistance so we cannot compute
                // distanceAlongRoute. Treat as idle — stay dormant rather than silently broken.
                Log.i(TAG, "Navigating to destination (no route distance) — staying dormant")
                resetState()
            }
            is OnNavigationState.NavigationState.Idle -> {
                Log.i(TAG, "Navigation idle — no route loaded")
                resetState()
            }
        }
    }

    // ── Distance tracking ─────────────────────────────────────────────────────

    /**
     * Called ~1Hz with the distance remaining to destination in metres.
     * Derives distanceAlongRoute and checks which climb window contains it.
     */
    private fun onDistanceUpdate(distanceToDestination: Double) {
        if (routeClimbs.isEmpty() || routeDistance <= 0.0) return

        // Fix #6: clamp to 0 to guard against GPS noise pushing distanceToDestination
        // fractionally above routeDistance, which would produce a negative distanceAlongRoute.
        val distanceAlongRoute = (routeDistance - distanceToDestination).coerceAtLeast(0.0)

        // Find which climb (if any) contains our current position
        val newClimbIndex = routeClimbs.indexOfFirst { climb ->
            distanceAlongRoute >= climb.startDistance &&
                    distanceAlongRoute <= climb.startDistance + climb.length
        }

        // Fix #5: only act on changes — identical index means nothing to do
        if (newClimbIndex == activeClimbIndex) return

        val previous = activeClimbIndex
        activeClimbIndex = newClimbIndex

        when {
            // Entered a climb from flat/descent
            previous == -1 && newClimbIndex != -1 -> {
                Log.i(TAG, "Entered climb $newClimbIndex at ${distanceAlongRoute}m — start lap")
                fireLap("start of climb $newClimbIndex", skipDebounce = false)
            }
            // Exited a climb to flat/descent
            previous != -1 && newClimbIndex == -1 -> {
                Log.i(TAG, "Exited climb $previous at ${distanceAlongRoute}m — end lap")
                fireLap("end of climb $previous", skipDebounce = false)
            }
            // Fix #2: direct climb-to-climb (back-to-back). Fire end lap for previous,
            // then immediately fire start lap for new climb bypassing debounce on the
            // second call — otherwise the start lap would always be suppressed.
            previous != -1 && newClimbIndex != -1 -> {
                Log.i(TAG, "Climb $previous → $newClimbIndex — end + start laps")
                fireLap("end of climb $previous", skipDebounce = false)
                fireLap("start of climb $newClimbIndex", skipDebounce = true)
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun resetState() {
        routeDistance = 0.0
        routeClimbs = emptyList()
        activeClimbIndex = -1
    }

    /**
     * Dispatches MarkLap.
     *
     * @param skipDebounce true only for the second lap in a back-to-back climb transition,
     * where the two laps are legitimately instantaneous and debounce would suppress the second.
     */
    private fun fireLap(reason: String, skipDebounce: Boolean) {
        val now = System.currentTimeMillis()
        if (!skipDebounce && now - lastLapAt < debounceMs) {
            Log.w(TAG, "Lap debounced ($reason) — ${now - lastLapAt}ms since last lap")
            return
        }
        lastLapAt = now
        karooSystem.dispatch(MarkLap())
        Log.i(TAG, "MarkLap dispatched ✓ ($reason)")
    }
}
