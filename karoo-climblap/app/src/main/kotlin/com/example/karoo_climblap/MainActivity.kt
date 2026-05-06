package com.example.karoo_climblap

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.karoo_climblap.databinding.ActivityMainBinding

/**
 * MainActivity is the settings screen shown when the user opens the app from the Karoo menu.
 *
 * Since the new NavigationState approach has no detection thresholds to tune,
 * this screen is intentionally simple: it explains what the extension does,
 * what's required (a route), and lets the user adjust the debounce guard.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("climb_lap_prefs", Context.MODE_PRIVATE)

        binding.editDebounceSeconds.setText(prefs.getInt("debounce_seconds", 10).toString())

        binding.btnSave.setOnClickListener {
            val debounce = binding.editDebounceSeconds.text.toString().toIntOrNull()
            if (debounce == null || debounce < 0) {
                Toast.makeText(this, "Please enter a valid number of seconds", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit().putInt("debounce_seconds", debounce).apply()
            Toast.makeText(this, getString(R.string.saved), Toast.LENGTH_SHORT).show()
        }
    }
}
