package com.example.karoo_climblap

import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension

private const val TAG = "ClimbLapExtension"

class ClimbLapExtension : KarooExtension("com.example.karoo_climblap", "1.0") {

    private lateinit var karooSystem: KarooSystemService
    private var climbLapService: ClimbLapService? = null

    override fun onCreate() {
        super.onCreate()
        karooSystem = KarooSystemService(applicationContext)
        karooSystem.connect {
            Log.i(TAG, "KarooSystemService connected")
            climbLapService = ClimbLapService(karooSystem, applicationContext)
            climbLapService?.start()
        }
    }

    override fun onDestroy() {
        climbLapService?.stop()
        climbLapService = null
        karooSystem.disconnect()
        super.onDestroy()
    }
}
