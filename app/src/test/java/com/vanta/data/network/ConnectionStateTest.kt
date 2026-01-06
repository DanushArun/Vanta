package com.vanta.data.network

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ConnectionState sealed class.
 */
class ConnectionStateTest {
    
    @Test
    fun `isActive returns true for Connected`() {
        val state = ConnectionState.Connected
        assertTrue(state.isActive)
        assertTrue(state.canSendMessages)
    }
    
    @Test
    fun `isActive returns true for Streaming`() {
        val state = ConnectionState.Streaming
        assertTrue(state.isActive)
        assertTrue(state.canSendMessages)
    }
    
    @Test
    fun `isActive returns true for Initializing`() {
        val state = ConnectionState.Initializing
        assertTrue(state.isActive)
        assertFalse(state.canSendMessages) // Can't send until setup complete
    }
    
    @Test
    fun `isActive returns false for Disconnected`() {
        val state = ConnectionState.Disconnected
        assertFalse(state.isActive)
        assertFalse(state.canSendMessages)
    }
    
    @Test
    fun `isActive returns false for Error`() {
        val state = ConnectionState.Error("test error")
        assertFalse(state.isActive)
        assertFalse(state.canSendMessages)
    }
    
    @Test
    fun `Reconnecting contains attempt info`() {
        val state = ConnectionState.Reconnecting(attempt = 3, maxAttempts = 5)
        assertFalse(state.isActive)
        assertFalse(state.canSendMessages)
        assertEquals(3, state.attempt)
        assertEquals(5, state.maxAttempts)
    }
    
    @Test
    fun `Error contains message and optional throwable`() {
        val throwable = RuntimeException("test")
        val state = ConnectionState.Error("Connection failed", throwable)
        
        assertEquals("Connection failed", state.message)
        assertEquals(throwable, state.throwable)
    }
}
