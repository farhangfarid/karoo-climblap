package com.example.karoo_climblap

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BootReceiver listens for the system BOOT_COMPLETED broadcast and starts
 * ClimbLapExtension as a foreground service so it's ready before any ride begins.
 *
 * Without this, the Karoo system would still bind to the extension when needed,
 * but adding it here means detection is definitely active from power-on.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, ClimbLapExtension::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
