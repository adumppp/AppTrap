package com.example.androidprivacyanalyzer

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

/**
 * App-wide entry point.
 *
 * Keep AppTrap in its light theme, using the logo's cyan and teal accents.
 */
class ApptrapApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }
}
