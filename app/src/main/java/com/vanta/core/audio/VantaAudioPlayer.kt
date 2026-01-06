package com.vanta.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.vanta.core.common.DispatcherProvider
import com.vanta.core.common.VantaLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages audio playback for Gemini responses.
 * 
 * Features:
 * - Streaming PCM playback
 * - Buffer management for smooth audio
 * - Pause/resume for barge-in handling
 * - Flush for interruption
 */
@Singleton
class VantaAudioPlayer @Inject constructor(
    private val dispatchers: DispatcherProvider
) {
    companion object {
        private const val TAG = "VantaAudioPlayer"
    }
    
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    
    // Audio buffer queue
    private val audioQueue = Channel<ByteArray>(capacity = Channel.UNLIMITED)
    
    // Playback state
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private var isPaused = false
    
    /**
     * Initialize the audio player.
     */
    fun initialize() {
        if (audioTrack != null) return
        
        val bufferSize = AudioTrack.getMinBufferSize(
            AudioConfig.OUTPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)
        
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(AudioConfig.OUTPUT_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        
        startPlaybackLoop()
        VantaLogger.i("Audio player initialized", tag = TAG)
    }
    
    /**
     * Enqueue audio data for playback.
     */
    suspend fun enqueue(pcmData: ByteArray) {
        audioQueue.send(pcmData)
    }
    
    /**
     * Pause playback immediately (for barge-in).
     */
    fun pause() {
        isPaused = true
        audioTrack?.pause()
        _isPlaying.value = false
        VantaLogger.d("Playback paused", tag = TAG)
    }
    
    /**
     * Resume playback.
     */
    fun resume() {
        isPaused = false
        audioTrack?.play()
        VantaLogger.d("Playback resumed", tag = TAG)
    }
    
    /**
     * Flush all buffered audio (for interruption).
     */
    fun flush() {
        // Clear the queue
        while (audioQueue.tryReceive().isSuccess) { /* drain */ }
        audioTrack?.flush()
        VantaLogger.d("Audio buffer flushed", tag = TAG)
    }
    
    /**
     * Stop and release.
     */
    fun release() {
        playbackJob?.cancel()
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        scope.cancel()
    }
    
    private fun startPlaybackLoop() {
        playbackJob = scope.launch {
            audioTrack?.play()
            
            for (chunk in audioQueue) {
                if (!isActive) break
                
                // Wait while paused
                while (isPaused && isActive) {
                    delay(50)
                }
                
                try {
                    val written = audioTrack?.write(chunk, 0, chunk.size) ?: -1
                    if (written > 0) {
                        _isPlaying.value = true
                    }
                } catch (e: Exception) {
                    VantaLogger.e("Playback error", e, tag = TAG)
                }
            }
            
            _isPlaying.value = false
        }
    }
}
