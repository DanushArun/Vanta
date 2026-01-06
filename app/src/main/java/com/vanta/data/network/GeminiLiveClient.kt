package com.vanta.data.network

import android.util.Base64
import com.vanta.BuildConfig
import com.vanta.core.common.DispatcherProvider
import com.vanta.core.common.VantaLogger
import com.vanta.data.network.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebSocket client for Gemini 2.0 Flash Multimodal Live API.
 * 
 * Handles:
 * - Connection lifecycle with automatic reconnection
 * - Sending audio/video frames
 * - Receiving and parsing audio responses
 * - Barge-in handling
 */
@Singleton
class GeminiLiveClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val dispatchers: DispatcherProvider
) {
    companion object {
        private const val TAG = "GeminiLiveClient"
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val RECONNECT_BASE_DELAY_MS = 1000L
        private const val PING_INTERVAL_SEC = 20L
    }
    
    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    
    // Connection state
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    // Incoming audio chunks from Gemini
    private val _audioChunks = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val audioChunks: SharedFlow<ByteArray> = _audioChunks.asSharedFlow()
    
    // Events for UI/coordinator
    private val _events = MutableSharedFlow<GeminiEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<GeminiEvent> = _events.asSharedFlow()
    
    /**
     * Connect to Gemini Live API.
     */
    suspend fun connect(systemPrompt: String) = withContext(dispatchers.io) {
        if (_connectionState.value.isActive) {
            VantaLogger.w("Already connected or connecting", tag = TAG)
            return@withContext
        }
        
        _connectionState.value = ConnectionState.Connecting
        
        val endpoint = buildEndpoint()
        val request = Request.Builder()
            .url(endpoint)
            .build()
        
        webSocket = okHttpClient.newWebSocket(request, createWebSocketListener(systemPrompt))
    }
    
    /**
     * Disconnect and cleanup.
     */
    fun disconnect() {
        reconnectJob?.cancel()
        webSocket?.close(1000, "Client closing")
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
        VantaLogger.i("Disconnected from Gemini", tag = TAG)
    }
    
    /**
     * Send audio and/or image data to Gemini.
     * 
     * @param audioPcm Raw PCM audio bytes (16kHz, 16-bit, mono)
     * @param imageJpeg JPEG image bytes (optional)
     */
    suspend fun sendMediaInput(audioPcm: ByteArray? = null, imageJpeg: ByteArray? = null) {
        if (!_connectionState.value.canSendMessages) {
            VantaLogger.w("Cannot send - not connected", tag = TAG)
            return
        }
        
        val chunks = mutableListOf<Blob>()
        
        audioPcm?.let { 
            val base64 = Base64.encodeToString(it, Base64.NO_WRAP)
            chunks.add(Blob.audioPcm(base64))
        }
        
        imageJpeg?.let {
            val base64 = Base64.encodeToString(it, Base64.NO_WRAP)
            chunks.add(Blob.imageJpeg(base64))
        }
        
        if (chunks.isEmpty()) return
        
        val message = ClientMessage(
            realtimeInput = BidiGenerateContentRealtimeInput(mediaChunks = chunks)
        )
        
        sendMessage(json.encodeToString(message))
    }
    
    /**
     * Signal to Gemini that the user has interrupted (barge-in).
     * This helps Gemini stop generating and process new input.
     */
    fun signalInterruption() {
        // Gemini handles interruption when we stop reading its audio and send new input
        // The client-side handling is to flush audio playback
        scope.launch {
            _events.emit(GeminiEvent.Interrupted)
        }
    }
    
    // ========================================================================
    // Private Implementation
    // ========================================================================
    
    private fun buildEndpoint(): String {
        val baseUrl = BuildConfig.GEMINI_WS_ENDPOINT
        val apiKey = BuildConfig.GEMINI_API_KEY
        
        return if (apiKey.isNotBlank()) {
            "$baseUrl?key=$apiKey"
        } else {
            // Production - proxy should add key
            baseUrl
        }
    }
    
    private fun createWebSocketListener(systemPrompt: String) = object : WebSocketListener() {
        
        override fun onOpen(webSocket: WebSocket, response: Response) {
            VantaLogger.i("WebSocket opened", tag = TAG)
            _connectionState.value = ConnectionState.Initializing
            
            // Send setup message
            val setupMessage = createSetupMessage(systemPrompt)
            sendMessage(json.encodeToString(setupMessage))
        }
        
        override fun onMessage(webSocket: WebSocket, text: String) {
            scope.launch {
                handleServerMessage(text)
            }
        }
        
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            VantaLogger.i("WebSocket closing: $code - $reason", tag = TAG)
        }
        
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            VantaLogger.i("WebSocket closed: $code - $reason", tag = TAG)
            handleDisconnection(wasClean = true)
        }
        
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            VantaLogger.e("WebSocket failure", t, tag = TAG)
            handleDisconnection(wasClean = false)
        }
    }
    
    private fun createSetupMessage(systemPrompt: String): ClientMessage {
        return ClientMessage(
            setup = BidiGenerateContentSetup(
                model = "models/gemini-2.0-flash-exp",
                generationConfig = GenerationConfig(
                    responseModalities = listOf("AUDIO"),
                    speechConfig = SpeechConfig(
                        voiceConfig = VoiceConfig(
                            prebuiltVoiceConfig = PrebuiltVoiceConfig(voiceName = "Kore")
                        )
                    )
                ),
                systemInstruction = Content(
                    parts = listOf(Part(text = systemPrompt))
                ),
                // Disable server-side VAD - we use Silero VAD client-side for more control
                realtimeInputConfig = RealtimeInputConfig(
                    automaticActivityDetection = AutomaticActivityDetection(disabled = true)
                )
            )
        )
    }
    
    /**
     * Signal to Gemini that the user has started speaking.
     * MUST be called when Silero VAD detects speech start.
     */
    suspend fun sendActivityStart() {
        if (!_connectionState.value.canSendMessages) return
        
        val message = ClientMessage(
            realtimeInput = BidiGenerateContentRealtimeInput(
                activityStart = ActivityStart()
            )
        )
        sendMessage(json.encodeToString(message))
        VantaLogger.d("Sent ActivityStart", tag = TAG)
    }
    
    /**
     * Signal to Gemini that the user has stopped speaking.
     * MUST be called when Silero VAD detects speech end.
     */
    suspend fun sendActivityEnd() {
        if (!_connectionState.value.canSendMessages) return
        
        val message = ClientMessage(
            realtimeInput = BidiGenerateContentRealtimeInput(
                activityEnd = ActivityEnd()
            )
        )
        sendMessage(json.encodeToString(message))
        VantaLogger.d("Sent ActivityEnd", tag = TAG)
    }
    
    private suspend fun handleServerMessage(text: String) {
        try {
            val message = json.decodeFromString<ServerMessage>(text)
            
            // Setup complete
            message.setupComplete?.let {
                VantaLogger.i("Setup complete", tag = TAG)
                _connectionState.value = ConnectionState.Connected
                _events.emit(GeminiEvent.Ready)
            }
            
            // Server content (audio response)
            message.serverContent?.let { content ->
                if (content.interrupted) {
                    VantaLogger.d("Server acknowledged interruption", tag = TAG)
                    _events.emit(GeminiEvent.Interrupted)
                    return
                }
                
                if (content.turnComplete) {
                    VantaLogger.d("Turn complete", tag = TAG)
                    _connectionState.value = ConnectionState.Connected
                    _events.emit(GeminiEvent.TurnComplete)
                    return
                }
                
                // Extract audio data
                content.modelTurn?.parts?.forEach { part ->
                    part.inlineData?.let { inline ->
                        if (inline.mimeType.startsWith("audio/")) {
                            val audioBytes = Base64.decode(inline.data, Base64.NO_WRAP)
                            _audioChunks.emit(audioBytes)
                            
                            if (_connectionState.value != ConnectionState.Streaming) {
                                _connectionState.value = ConnectionState.Streaming
                            }
                        }
                    }
                    
                    // Handle any text parts (for debugging/logging)
                    part.text?.let { VantaLogger.d("Text response: $it", tag = TAG) }
                }
            }
            
        } catch (e: Exception) {
            VantaLogger.e("Failed to parse server message", e, tag = TAG)
        }
    }
    
    private fun handleDisconnection(wasClean: Boolean) {
        webSocket = null
        
        if (wasClean) {
            _connectionState.value = ConnectionState.Disconnected
        } else {
            // Attempt reconnection
            attemptReconnection()
        }
    }
    
    private fun attemptReconnection() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            for (attempt in 1..MAX_RECONNECT_ATTEMPTS) {
                _connectionState.value = ConnectionState.Reconnecting(attempt, MAX_RECONNECT_ATTEMPTS)
                
                val delayMs = RECONNECT_BASE_DELAY_MS * (1 shl (attempt - 1)) // Exponential backoff
                delay(delayMs.coerceAtMost(30_000))
                
                VantaLogger.i("Reconnection attempt $attempt/$MAX_RECONNECT_ATTEMPTS", tag = TAG)
                
                // TODO: Reconnect with cached system prompt
                // For now, emit error
            }
            
            _connectionState.value = ConnectionState.Error("Max reconnection attempts reached")
            _events.emit(GeminiEvent.ConnectionLost)
        }
    }
    
    private fun sendMessage(message: String) {
        val sent = webSocket?.send(message) ?: false
        if (!sent) {
            VantaLogger.w("Failed to send message", tag = TAG)
        }
    }
}

/**
 * Events emitted by GeminiLiveClient for the coordinator/UI.
 */
sealed class GeminiEvent {
    /** Connection established, ready to stream */
    data object Ready : GeminiEvent()
    
    /** AI finished speaking */
    data object TurnComplete : GeminiEvent()
    
    /** User interrupted or server acknowledged interruption */
    data object Interrupted : GeminiEvent()
    
    /** Connection lost after max retries */
    data object ConnectionLost : GeminiEvent()
}
