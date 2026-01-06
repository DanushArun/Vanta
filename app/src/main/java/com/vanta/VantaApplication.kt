package com.vanta

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Vanta Application - Entry point for the app.
 * 
 * Initializes Hilt dependency injection and any app-wide configurations.
 */
@HiltAndroidApp
class VantaApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        // App-wide initialization will go here
        // - Logging setup
        // - Crash reporting
        // - Feature flags
    }
}
