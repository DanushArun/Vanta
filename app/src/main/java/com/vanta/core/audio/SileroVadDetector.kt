package com.vanta.core.audio

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.vanta.core.common.DispatcherProvider
import com.vanta.core.common.VantaLogger
import com.vanta.core.config.VantaConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Silero VAD (Voice Activity Detection) implementation using ONNX Runtime.
 * 
 * Why Silero VAD over simple RMS:
 * - Deep learning model trained on real speech data
 * - Robust to background noise
 * - Better detection of speech start/end boundaries
 * - Provides speech probability (0-1) not just threshold
 * 
 * Model: silero_vad.onnx (must be placed in assets folder)
 * - Input: 512 samples at 16kHz (32ms chunks)
 * - Output: Speech probability [0, 1]
 */
@Singleton
class SileroVadDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: VantaConfig,
    private val dispatchers: DispatcherProvider
) {
    companion object {
        private const val TAG = "SileroVadDetector"
        private const val MODEL_FILE = "silero_vad.onnx"
        
        // Silero VAD requires 512 samples at 16kHz
        private const val SAMPLES_PER_CHUNK = 512
        private const val SAMPLE_RATE = 16000
        
        // Thresholds for speech detection
        private const val SPEECH_THRESHOLD = 0.5f
        private const val SILENCE_THRESHOLD = 0.35f
        
        // Timing
        private const val SPEECH_PAD_MS = 300  // Pad speech with silence
        private const val MIN_SPEECH_MS = 250  // Minimum speech duration
        private const val MIN_SILENCE_MS = 100 // Minimum silence to end speech
    }
    
    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    
    // Internal state for VAD
    private var h = FloatArray(2 * 1 * 64) // Hidden state (2 layers, 1 batch, 64 hidden)
    private var c = FloatArray(2 * 1 * 64) // Cell state
    
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    
    // Speech state
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()
    
    private val _speechProbability = MutableStateFlow(0f)
    val speechProbability: StateFlow<Float> = _speechProbability.asStateFlow()
    
    // Events for activity signaling
    sealed class VadEvent {
        data object SpeechStart : VadEvent()
        data object SpeechEnd : VadEvent()
    }
    
    private val _events = MutableSharedFlow<VadEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<VadEvent> = _events.asSharedFlow()
    
    // State tracking
    private var isSpeechActive = false
    private var speechStartTime = 0L
    private var silenceStartTime = 0L
    
    /**
     * Initialize the ONNX Runtime and load Silero VAD model.
     */
    suspend fun initialize() = withContext(dispatchers.io) {
        try {
            ortEnvironment = OrtEnvironment.getEnvironment()
            
            // Load model from assets
            val modelBytes = context.assets.open(MODEL_FILE).use { it.readBytes() }
            ortSession = ortEnvironment?.createSession(modelBytes)
            
            VantaLogger.i("Silero VAD initialized", tag = TAG)
        } catch (e: Exception) {
            VantaLogger.e("Failed to initialize Silero VAD", e, tag = TAG)
            throw e
        }
    }
    
    /**
     * Process an audio chunk and detect speech.
     * 
     * @param pcmData PCM audio bytes (16kHz, 16-bit, mono)
     * @return Speech probability [0, 1]
     */
    suspend fun processAudioChunk(pcmData: ByteArray): Float = withContext(dispatchers.default) {
        val session = ortSession ?: run {
            VantaLogger.w("VAD not initialized", tag = TAG)
            return@withContext 0f
        }
        
        // Convert PCM bytes to float samples
        val samples = pcmBytesToFloatSamples(pcmData)
        
        // Process in 512-sample chunks
        var lastProbability = 0f
        for (i in samples.indices step SAMPLES_PER_CHUNK) {
            val chunk = samples.sliceArray(i until minOf(i + SAMPLES_PER_CHUNK, samples.size))
            if (chunk.size == SAMPLES_PER_CHUNK) {
                lastProbability = runInference(session, chunk)
            }
        }
        
        _speechProbability.value = lastProbability
        updateSpeechState(lastProbability)
        
        lastProbability
    }
    
    private fun runInference(session: OrtSession, samples: FloatArray): Float {
        val env = ortEnvironment ?: return 0f
        
        try {
            // Prepare input tensors
            val inputTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(samples),
                longArrayOf(1, samples.size.toLong())
            )
            
            val sr = OnnxTensor.createTensor(env, longArrayOf(SAMPLE_RATE.toLong()))
            
            val hTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(h),
                longArrayOf(2, 1, 64)
            )
            
            val cTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(c),
                longArrayOf(2, 1, 64)
            )
            
            // Run inference
            val inputs = mapOf(
                "input" to inputTensor,
                "sr" to sr,
                "h" to hTensor,
                "c" to cTensor
            )
            
            val results = session.run(inputs)
            
            // Extract outputs
            val output = results[0].value as Array<FloatArray>
            val hn = results[1].value as Array<Array<FloatArray>>
            val cn = results[2].value as Array<Array<FloatArray>>
            
            // Update hidden states for next iteration
            for (i in 0 until 2) {
                for (j in 0 until 64) {
                    h[i * 64 + j] = hn[i][0][j]
                    c[i * 64 + j] = cn[i][0][j]
                }
            }
            
            // Clean up tensors
            inputTensor.close()
            sr.close()
            hTensor.close()
            cTensor.close()
            results.close()
            
            return output[0][0]
            
        } catch (e: Exception) {
            VantaLogger.e("VAD inference failed", e, tag = TAG)
            return 0f
        }
    }
    
    private suspend fun updateSpeechState(probability: Float) {
        val now = System.currentTimeMillis()
        
        if (!isSpeechActive && probability >= SPEECH_THRESHOLD) {
            // Speech started
            isSpeechActive = true
            speechStartTime = now
            _isSpeaking.value = true
            _events.emit(VadEvent.SpeechStart)
            VantaLogger.d("Speech started (p=$probability)", tag = TAG)
            
        } else if (isSpeechActive && probability < SILENCE_THRESHOLD) {
            // Potential speech end
            if (silenceStartTime == 0L) {
                silenceStartTime = now
            }
            
            val silenceDuration = now - silenceStartTime
            val speechDuration = now - speechStartTime
            
            if (silenceDuration >= MIN_SILENCE_MS && speechDuration >= MIN_SPEECH_MS) {
                // Speech ended
                isSpeechActive = false
                silenceStartTime = 0L
                _isSpeaking.value = false
                _events.emit(VadEvent.SpeechEnd)
                VantaLogger.d("Speech ended (duration=${speechDuration}ms)", tag = TAG)
            }
            
        } else if (isSpeechActive && probability >= SILENCE_THRESHOLD) {
            // Reset silence timer - still speaking
            silenceStartTime = 0L
        }
    }
    
    private fun pcmBytesToFloatSamples(pcmData: ByteArray): FloatArray {
        val samples = FloatArray(pcmData.size / 2)
        for (i in samples.indices) {
            val low = pcmData[i * 2].toInt() and 0xFF
            val high = pcmData[i * 2 + 1].toInt()
            val sample = (high shl 8) or low
            samples[i] = sample / 32768f  // Normalize to [-1, 1]
        }
        return samples
    }
    
    /**
     * Reset VAD state (call when starting new conversation).
     */
    fun reset() {
        h.fill(0f)
        c.fill(0f)
        isSpeechActive = false
        speechStartTime = 0L
        silenceStartTime = 0L
        _isSpeaking.value = false
        _speechProbability.value = 0f
    }
    
    /**
     * Release resources.
     */
    fun release() {
        ortSession?.close()
        ortEnvironment?.close()
        scope.cancel()
    }
}
