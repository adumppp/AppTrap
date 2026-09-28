package com.example.androidprivacyanalyzer

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

/**
 * App-wide entry point.
 *
 * The polished reference prototype is a dark slate/indigo design, so the app
 * always renders in dark mode to match it 1:1. Change to
 * MODE_NIGHT_FOLLOW_SYSTEM if the light theme should be user-controlled again.
 */
class ApptrapApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    }
}