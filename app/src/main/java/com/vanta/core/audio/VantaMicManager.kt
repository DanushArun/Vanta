package com.vanta.core.audio

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import androidx.annotation.RequiresPermission
import com.vanta.core.common.DispatcherProvider
import com.vanta.core.common.VantaLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages microphone input for capturing audio to send to Gemini.
 * 
 * Features:
 * - Acoustic Echo Cancellation (prevents AI hearing itself)
 * - Noise Suppression
 * - Voice Activity Detection for barge-in
 */
@Singleton
class VantaMicManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider
) {
    companion object {
        private const val TAG = "VantaMicManager"
    }
    
    private var audioRecord: AudioRecord? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    private var recordingJob: Job? = null
    
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    
    // Audio output as PCM chunks
    private val _audioChunks = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val audioChunks: SharedFlow<ByteArray> = _audioChunks.asSharedFlow()
    
    // Voice Activity Detection state
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()
    
    private var isRecording = false
    
    /**
     * Start recording audio.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        if (isRecording) return
        
        val bufferSize = AudioRecord.getMinBufferSize(
            AudioConfig.INPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)
        
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, // Enables platform AEC
            AudioConfig.INPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 2
        )
        
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            VantaLogger.e("AudioRecord failed to initialize", tag = TAG)
            return
        }
        
        // Enable Acoustic Echo Cancellation
        val sessionId = audioRecord?.audioSessionId ?: 0
        if (AcousticEchoCanceler.isAvailable()) {
            aec = AcousticEchoCanceler.create(sessionId)?.apply {
                enabled = true
                VantaLogger.i("AEC enabled", tag = TAG)
            }
        }
        
        // Enable Noise Suppression
        if (NoiseSuppressor.isAvailable()) {
            ns = NoiseSuppressor.create(sessionId)?.apply {
                enabled = true
                VantaLogger.i("Noise suppression enabled", tag = TAG)
            }
        }
        
        audioRecord?.startRecording()
        isRecording = true
        
        startRecordingLoop(bufferSize)
        VantaLogger.i("Microphone started", tag = TAG)
    }
    
    /**
     * Stop recording.
     */
    fun stop() {
        recordingJob?.cancel()
        audioRecord?.stop()
        isRecording = false
        VantaLogger.i("Microphone stopped", tag = TAG)
    }
    
    /**
     * Release all resources.
     */
    fun release() {
        stop()
        aec?.release()
        ns?.release()
        audioRecord?.release()
        audioRecord = null
        scope.cancel()
    }
    
    private fun startRecordingLoop(bufferSize: Int) {
        recordingJob = scope.launch {
            val buffer = ByteArray(bufferSize)
            val rmsHistory = mutableListOf<Int>()
            
            while (isActive && isRecording) {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                
                if (bytesRead > 0) {
                    // Emit audio chunk
                    _audioChunks.emit(buffer.copyOf(bytesRead))
                    
                    // Voice Activity Detection
                    val rms = calculateRMS(buffer, bytesRead)
                    updateVAD(rms, rmsHistory)
                }
            }
        }
    }
    
    private fun calculateRMS(buffer: ByteArray, length: Int): Int {
        var sum = 0L
        for (i in 0 until length step 2) {
            if (i + 1 < length) {
                val sample = (buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)
                sum += sample * sample
            }
        }
        return kotlin.math.sqrt((sum / (length / 2)).toDouble()).toInt()
    }
    
    private fun updateVAD(rms: Int, history: MutableList<Int>) {
        history.add(rms)
        if (history.size > AudioConfig.VAD_WINDOW_SIZE) {
            history.removeAt(0)
        }
        
        val avgRms = history.average().toInt()
        val speaking = avgRms > AudioConfig.VAD_RMS_THRESHOLD
        
        if (speaking != _isSpeaking.value) {
            _isSpeaking.value = speaking
            VantaLogger.d("VAD: speaking=$speaking (rms=$avgRms)", tag = TAG)
        }
    }
}
