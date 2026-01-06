package com.vanta.core.audio

/**
 * Audio configuration constants used across the audio pipeline.
 * Matches Gemini API requirements.
 */
object AudioConfig {
    // Input (Microphone → Gemini)
    const val INPUT_SAMPLE_RATE = 16000        // 16kHz required by Gemini
    const val INPUT_CHANNELS = 1               // Mono
    const val INPUT_BITS_PER_SAMPLE = 16       // 16-bit PCM
    const val INPUT_CHUNK_DURATION_MS = 100    // 100ms chunks
    
    // Output (Gemini → Speaker)
    const val OUTPUT_SAMPLE_RATE = 24000       // 24kHz from Gemini
    const val OUTPUT_CHANNELS = 1              // Mono
    const val OUTPUT_BITS_PER_SAMPLE = 16      // 16-bit PCM
    const val OUTPUT_BUFFER_MS = 150           // Buffer for smooth playback
    
    // Voice Activity Detection
    const val VAD_RMS_THRESHOLD = 800          // Calibrated for speech detection
    const val VAD_HOLD_TIME_MS = 300L          // Debounce duration
    const val VAD_WINDOW_SIZE = 3              // Rolling window for smoothing
}
