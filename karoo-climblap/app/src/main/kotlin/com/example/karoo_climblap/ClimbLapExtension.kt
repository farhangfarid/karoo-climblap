package com.example.karoo_climblap

import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension

private const val TAG = "ClimbLapExtension"

/**
 * ClimbLapExtension is the entry point declared in the manifest.
 *
 * KarooExtension is an Android Service. The Karoo system binds to it on boot
 * and whenever a ride starts. We override onServiceConnected to kick off our
 * climb-detection logic.
 *
 * Keep this class thin — all real logic lives in ClimbLapService.
 */
class ClimbLapExtension : KarooExtension("com.example.karoo_climblap", "1.0") {

    // karooSystem lets us subscribe to sensor streams and dispatch effects (e.g. MarkLap)
    private lateinit var karooSystem: KarooSystemService

    // Our climb-detection brain
    private var climbLapService: ClimbLapService? = null

    override fun onCreate() {
        super.onCreate()
        karooSystem = KarooSystemService(applicationContext)
        // connect() must be called before addConsumer or dispatch will work.
        // The callback fires once the IPC channel to Karoo OS is ready.
        karooSystem.connect { Log.i(TAG, "KarooSystemService connected") }
    }

    /**
     * Called by the Karoo system when it has connected to our extension.
     * This is where we start listening.
     */
    override fun onServiceConnected() {
        climbLapService = ClimbLapService(karooSystem, applicationContext)
        climbLapService?.start()
    }

    /**
     * Called when the Karoo system disconnects (e.g. device shutdown).
     * Clean up coroutines and listeners.
     */
    override fun onServiceDisconnected() {
        climbLapService?.stop()
        climbLapService = null
    }

    override fun onDestroy() {
        climbLapService?.stop()
        karooSystem.disconnect()
        super.onDestroy()
    }
}
