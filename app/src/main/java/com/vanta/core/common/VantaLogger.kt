package com.vanta.core.common

import android.util.Log
import com.vanta.BuildConfig

/**
 * Centralized logging utility.
 * Wraps Android Log with tag management and debug-only logging.
 */
object VantaLogger {
    private const val TAG = "Vanta"
    
    private val isDebug: Boolean = BuildConfig.DEBUG
    
    fun d(message: String, tag: String = TAG) {
        if (isDebug) Log.d(tag, message)
    }
    
    fun i(message: String, tag: String = TAG) {
        Log.i(tag, message)
    }
    
    fun w(message: String, throwable: Throwable? = null, tag: String = TAG) {
        if (throwable != null) {
            Log.w(tag, message, throwable)
        } else {
            Log.w(tag, message)
        }
    }
    
    fun e(message: String, throwable: Throwable? = null, tag: String = TAG) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
}
