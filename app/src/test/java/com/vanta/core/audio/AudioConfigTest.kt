package com.vanta.core.audio

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for AudioConfig constants.
 */
class AudioConfigTest {
    
    @Test
    fun `input sample rate is 16kHz for Gemini`() {
        assertEquals(16000, AudioConfig.INPUT_SAMPLE_RATE)
    }
    
    @Test
    fun `output sample rate is 24kHz for Gemini`() {
        assertEquals(24000, AudioConfig.OUTPUT_SAMPLE_RATE)
    }
    
    @Test
    fun `input and output are mono`() {
        assertEquals(1, AudioConfig.INPUT_CHANNELS)
        assertEquals(1, AudioConfig.OUTPUT_CHANNELS)
    }
    
    @Test
    fun `bits per sample is 16-bit PCM`() {
        assertEquals(16, AudioConfig.INPUT_BITS_PER_SAMPLE)
        assertEquals(16, AudioConfig.OUTPUT_BITS_PER_SAMPLE)
    }
    
    @Test
    fun `VAD threshold is reasonable`() {
        // RMS threshold should be positive and reasonable for speech detection
        assertTrue(AudioConfig.VAD_RMS_THRESHOLD > 0)
        assertTrue(AudioConfig.VAD_RMS_THRESHOLD < 5000) // Not too high
    }
    
    @Test
    fun `VAD hold time prevents rapid toggling`() {
        // Should be > 100ms to prevent jitter
        assertTrue(AudioConfig.VAD_HOLD_TIME_MS >= 100)
    }
}
