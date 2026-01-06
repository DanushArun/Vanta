package com.vanta.data.network

/**
 * Represents the current state of the WebSocket connection to Gemini.
 */
sealed class ConnectionState {
    /** Not connected, idle state */
    data object Disconnected : ConnectionState()
    
    /** Actively trying to establish connection */
    data object Connecting : ConnectionState()
    
    /** WebSocket open, setup message sent, waiting for setupComplete */
    data object Initializing : ConnectionState()
    
    /** Fully connected and ready to stream */
    data object Connected : ConnectionState()
    
    /** AI is currently generating a response */
    data object Streaming : ConnectionState()
    
    /** Connection lost, attempting to reconnect */
    data class Reconnecting(val attempt: Int, val maxAttempts: Int) : ConnectionState()
    
    /** Fatal error, cannot recover */
    data class Error(val message: String, val throwable: Throwable? = null) : ConnectionState()
    
    val isActive: Boolean
        get() = this is Connected || this is Streaming || this is Initializing
    
    val canSendMessages: Boolean
        get() = this is Connected || this is Streaming
}
