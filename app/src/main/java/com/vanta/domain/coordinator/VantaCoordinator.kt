package com.vanta.domain.coordinator

import com.vanta.core.audio.SileroVadDetector
import com.vanta.core.audio.VantaAudioPlayer
import com.vanta.core.audio.VantaMicManager
import com.vanta.core.camera.VantaCameraManager
import com.vanta.core.common.DispatcherProvider
import com.vanta.core.common.VantaLogger
import com.vanta.core.config.SystemPrompts
import com.vanta.core.config.VantaMode
import com.vanta.data.network.ConnectionState
import com.vanta.data.network.GeminiEvent
import com.vanta.data.network.GeminiLiveClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central coordinator that orchestrates all Vanta components.
 * 
 * Responsibilities:
 * - Start/stop all pipelines
 * - Route camera frames and audio to Gemini
 * - Handle barge-in using Silero VAD
 * - Send ActivityStart/ActivityEnd signals for speech boundaries
 * - Manage connection state for UI
 */
@Singleton
class VantaCoordinator @Inject constructor(
    private val geminiClient: GeminiLiveClient,
    private val cameraManager: VantaCameraManager,
    private val micManager: VantaMicManager,
    private val audioPlayer: VantaAudioPlayer,
    private val sileroVad: SileroVadDetector,
    private val dispatchers: DispatcherProvider
) {
    companion object {
        private const val TAG = "VantaCoordinator"
    }
    
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private var isRunning = false
    private var currentMode = VantaMode.SOCIAL
    
    // Expose connection state for UI
    val connectionState: StateFlow<ConnectionState> = geminiClient.connectionState
    
    // Combined state for simple UI binding
    sealed class VantaState {
        data object Idle : VantaState()
        data object Connecting : VantaState()
        data object Listening : VantaState()  // Ready, waiting for user
        data object Speaking : VantaState()   // AI is responding
        data object UserSpeaking : VantaState() // User is speaking (VAD detected)
        data class Error(val message: String) : VantaState()
    }
    
    private val _state = MutableStateFlow<VantaState>(VantaState.Idle)
    val state: StateFlow<VantaState> = _state.asStateFlow()
    
    // Expose speech probability for UI visualization
    val speechProbability: StateFlow<Float> = sileroVad.speechProbability
    
    /**
     * Start all Vanta systems.
     */
    suspend fun start(
        lifecycleOwner: androidx.lifecycle.LifecycleOwner,
        mode: VantaMode = VantaMode.SOCIAL
    ) {
        if (isRunning) return
        isRunning = true
        currentMode = mode
        
        VantaLogger.i("Starting Vanta in $mode mode", tag = TAG)
        _state.value = VantaState.Connecting
        
        // Initialize Silero VAD first
        sileroVad.initialize()
        
        // Initialize other components
        audioPlayer.initialize()
        micManager.start()
        cameraManager.start(lifecycleOwner)
        
        // Connect to Gemini with mode-specific prompt
        val systemPrompt = SystemPrompts.forMode(mode)
        geminiClient.connect(systemPrompt)
        
        // Start pipeline coroutines
        launchCameraForwarder()
        launchAudioForwarder()
        launchResponseHandler()
        launchVadHandler()
        launchStateMapper()
    }
    
    /**
     * Stop all Vanta systems.
     */
    fun stop() {
        VantaLogger.i("Stopping Vanta", tag = TAG)
        isRunning = false
        
        scope.coroutineContext.cancelChildren()
        
        geminiClient.disconnect()
        cameraManager.stop()
        micManager.stop()
        audioPlayer.release()
        sileroVad.release()
        
        _state.value = VantaState.Idle
    }
    
    /**
     * Switch operating mode.
     */
    suspend fun switchMode(mode: VantaMode) {
        if (mode == currentMode) return
        
        VantaLogger.i("Switching to $mode mode", tag = TAG)
        currentMode = mode
        
        // Need to reconnect with new system prompt
        geminiClient.disconnect()
        sileroVad.reset()
        
        val systemPrompt = SystemPrompts.forMode(mode)
        geminiClient.connect(systemPrompt)
    }
    
    // ========================================================================
    // Pipeline Coroutines
    // ========================================================================
    
    private fun launchCameraForwarder() = scope.launch {
        cameraManager.frames.collect { jpegBytes ->
            geminiClient.sendMediaInput(imageJpeg = jpegBytes)
        }
    }
    
    private fun launchAudioForwarder() = scope.launch {
        micManager.audioChunks.collect { pcmBytes ->
            // Process through Silero VAD
            sileroVad.processAudioChunk(pcmBytes)
            
            // Forward to Gemini
            geminiClient.sendMediaInput(audioPcm = pcmBytes)
        }
    }
    
    private fun launchResponseHandler() = scope.launch {
        geminiClient.audioChunks.collect { pcmBytes ->
            audioPlayer.enqueue(pcmBytes)
        }
    }
    
    /**
     * Handle Silero VAD events - this is the key integration.
     * 
     * When VAD detects speech start:
     * - Pause AI audio playback (barge-in)
     * - Send ActivityStart to Gemini
     * 
     * When VAD detects speech end:
     * - Send ActivityEnd to Gemini (triggers AI response)
     */
    private fun launchVadHandler() = scope.launch {
        sileroVad.events.collect { event ->
            when (event) {
                is SileroVadDetector.VadEvent.SpeechStart -> {
                    VantaLogger.i("VAD: Speech started - barge-in", tag = TAG)
                    
                    // Barge-in: pause AI playback
                    if (audioPlayer.isPlaying.value) {
                        audioPlayer.pause()
                        audioPlayer.flush()
                    }
                    
                    // Signal to Gemini
                    geminiClient.sendActivityStart()
                    geminiClient.signalInterruption()
                }
                
                is SileroVadDetector.VadEvent.SpeechEnd -> {
                    VantaLogger.i("VAD: Speech ended - triggering response", tag = TAG)
                    
                    // Signal to Gemini to start generating response
                    geminiClient.sendActivityEnd()
                }
            }
        }
    }
    
    private fun launchStateMapper() = scope.launch {
        combine(
            geminiClient.connectionState,
            audioPlayer.isPlaying,
            sileroVad.isSpeaking
        ) { connState, isPlaying, isSpeaking ->
            when {
                connState is ConnectionState.Error -> VantaState.Error(connState.message)
                connState is ConnectionState.Connecting || 
                connState is ConnectionState.Initializing ||
                connState is ConnectionState.Reconnecting -> VantaState.Connecting
                isSpeaking -> VantaState.UserSpeaking
                isPlaying -> VantaState.Speaking
                connState is ConnectionState.Connected -> VantaState.Listening
                else -> VantaState.Idle
            }
        }.collect { _state.value = it }
    }
}
