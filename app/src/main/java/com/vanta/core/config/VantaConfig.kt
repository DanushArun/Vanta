package com.vanta.core.config

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized configuration management.
 * 
 * Loads settings from:
 * 1. .env file (for local development)
 * 2. BuildConfig (for release builds)
 * 3. Default values (fallback)
 * 
 * All configuration access goes through this class for:
 * - Easy testing (mock the config)
 * - Centralized validation
 * - Clear documentation of all settings
 */
@Singleton
class VantaConfig @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val envProperties: Properties by lazy { loadEnvFile() }
    
    // ========================================================================
    // Gemini API Configuration
    // ========================================================================
    
    val geminiApiKey: String
        get() = getEnvValue("GEMINI_API_KEY", com.vanta.BuildConfig.GEMINI_API_KEY)
    
    val geminiWebSocketEndpoint: String
        get() = getEnvValue("GEMINI_PROXY_URL", com.vanta.BuildConfig.GEMINI_WS_ENDPOINT)
    
    val geminiModel: String
        get() = getEnvValue("GEMINI_MODEL", "models/gemini-2.0-flash-exp")
    
    val geminiVoice: String
        get() = getEnvValue("GEMINI_VOICE", "Kore")
    
    // ========================================================================
    // Audio Configuration
    // ========================================================================
    
    val audioInputSampleRate: Int
        get() = getEnvValue("AUDIO_INPUT_SAMPLE_RATE", "16000").toIntOrNull() ?: 16000
    
    val audioOutputSampleRate: Int
        get() = getEnvValue("AUDIO_OUTPUT_SAMPLE_RATE", "24000").toIntOrNull() ?: 24000
    
    val vadRmsThreshold: Int
        get() = getEnvValue("VAD_RMS_THRESHOLD", "800").toIntOrNull() ?: 800
    
    val vadHoldTimeMs: Long
        get() = getEnvValue("VAD_HOLD_TIME_MS", "300").toLongOrNull() ?: 300L
    
    // ========================================================================
    // Camera Configuration
    // ========================================================================
    
    val cameraFrameRate: Int
        get() = getEnvValue("CAMERA_FRAME_RATE", "2").toIntOrNull()?.coerceIn(1, 4) ?: 2
    
    val cameraJpegQuality: Int
        get() = getEnvValue("CAMERA_JPEG_QUALITY", "50").toIntOrNull()?.coerceIn(10, 100) ?: 50
    
    val cameraResolutionWidth: Int
        get() = getEnvValue("CAMERA_WIDTH", "640").toIntOrNull() ?: 640
    
    val cameraResolutionHeight: Int
        get() = getEnvValue("CAMERA_HEIGHT", "480").toIntOrNull() ?: 480
    
    // ========================================================================
    // Network Configuration
    // ========================================================================
    
    val reconnectMaxAttempts: Int
        get() = getEnvValue("RECONNECT_MAX_ATTEMPTS", "5").toIntOrNull() ?: 5
    
    val reconnectBaseDelayMs: Long
        get() = getEnvValue("RECONNECT_BASE_DELAY_MS", "1000").toLongOrNull() ?: 1000L
    
    val pingIntervalSeconds: Long
        get() = getEnvValue("PING_INTERVAL_SECONDS", "20").toLongOrNull() ?: 20L
    
    // ========================================================================
    // Feature Flags
    // ========================================================================
    
    val isDebugLoggingEnabled: Boolean
        get() = getEnvValue("ENABLE_DEBUG_LOGGING", com.vanta.BuildConfig.DEBUG.toString()).toBoolean()
    
    val isMlSafetyEnabled: Boolean
        get() = getEnvValue("ENABLE_ML_SAFETY", "false").toBoolean()
    
    val isHapticsEnabled: Boolean
        get() = getEnvValue("ENABLE_HAPTICS", "true").toBoolean()
    
    // ========================================================================
    // Private Helpers
    // ========================================================================
    
    private fun loadEnvFile(): Properties {
        val properties = Properties()
        try {
            // Try to load from assets (for development)
            context.assets.open(".env").use { 
                properties.load(it)
            }
        } catch (e: Exception) {
            // .env file not found in assets - this is expected in production
        }
        
        // Also try external storage for development overrides
        try {
            val externalEnv = File(context.getExternalFilesDir(null), ".env")
            if (externalEnv.exists()) {
                externalEnv.inputStream().use {
                    properties.load(it)
                }
            }
        } catch (e: Exception) {
            // Ignore - external .env is optional
        }
        
        return properties
    }
    
    private fun getEnvValue(key: String, default: String): String {
        // Priority: .env file > BuildConfig > default
        return envProperties.getProperty(key) 
            ?: System.getenv(key) 
            ?: default
    }
    
    /**
     * Validate configuration on startup.
     * Returns list of errors if any.
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        
        if (geminiApiKey.isBlank()) {
            errors.add("GEMINI_API_KEY is not set")
        }
        
        if (audioInputSampleRate !in listOf(8000, 16000, 44100, 48000)) {
            errors.add("AUDIO_INPUT_SAMPLE_RATE must be 8000, 16000, 44100, or 48000")
        }
        
        return errors
    }
    
    /**
     * Get all configuration as a map (for debugging).
     * Sensitive values are masked.
     */
    fun toDebugMap(): Map<String, String> = mapOf(
        "geminiApiKey" to if (geminiApiKey.isNotBlank()) "****${geminiApiKey.takeLast(4)}" else "(not set)",
        "geminiModel" to geminiModel,
        "geminiVoice" to geminiVoice,
        "audioInputSampleRate" to audioInputSampleRate.toString(),
        "audioOutputSampleRate" to audioOutputSampleRate.toString(),
        "cameraFrameRate" to cameraFrameRate.toString(),
        "cameraJpegQuality" to cameraJpegQuality.toString(),
        "isDebugLoggingEnabled" to isDebugLoggingEnabled.toString(),
        "isMlSafetyEnabled" to isMlSafetyEnabled.toString()
    )
}
