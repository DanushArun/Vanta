package com.vanta.data.network.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Gemini API message serialization.
 */
class GeminiMessagesTest {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true 
    }
    
    @Test
    fun `SetupMessage serializes correctly`() {
        val message = ClientMessage(
            setup = BidiGenerateContentSetup(
                model = "models/gemini-2.0-flash-exp",
                generationConfig = GenerationConfig(
                    responseModalities = listOf("AUDIO")
                ),
                systemInstruction = Content(
                    parts = listOf(Part(text = "You are a helpful assistant"))
                )
            )
        )
        
        val serialized = json.encodeToString(message)
        
        assertTrue(serialized.contains("gemini-2.0-flash-exp"))
        assertTrue(serialized.contains("AUDIO"))
        assertTrue(serialized.contains("helpful assistant"))
    }
    
    @Test
    fun `RealtimeInput with audio serializes correctly`() {
        val message = ClientMessage(
            realtimeInput = BidiGenerateContentRealtimeInput(
                mediaChunks = listOf(Blob.audioPcm("dGVzdGF1ZGlv"))
            )
        )
        
        val serialized = json.encodeToString(message)
        
        assertTrue(serialized.contains("audio/pcm"))
        assertTrue(serialized.contains("dGVzdGF1ZGlv"))
    }
    
    @Test
    fun `RealtimeInput with image serializes correctly`() {
        val message = ClientMessage(
            realtimeInput = BidiGenerateContentRealtimeInput(
                mediaChunks = listOf(Blob.imageJpeg("aW1hZ2VkYXRh"))
            )
        )
        
        val serialized = json.encodeToString(message)
        
        assertTrue(serialized.contains("image/jpeg"))
        assertTrue(serialized.contains("aW1hZ2VkYXRh"))
    }
    
    @Test
    fun `ActivityStart message serializes correctly`() {
        val message = ClientMessage(
            realtimeInput = BidiGenerateContentRealtimeInput(
                activityStart = ActivityStart()
            )
        )
        
        val serialized = json.encodeToString(message)
        assertTrue(serialized.contains("activity_start"))
    }
    
    @Test
    fun `ServerMessage with setupComplete deserializes`() {
        val serverJson = """{"setupComplete":{"model":"gemini-2.0-flash-exp"}}"""
        val message = json.decodeFromString<ServerMessage>(serverJson)
        
        assertNotNull(message.setupComplete)
        assertEquals("gemini-2.0-flash-exp", message.setupComplete?.model)
    }
    
    @Test
    fun `ServerMessage with serverContent deserializes`() {
        val serverJson = """{"serverContent":{"turn_complete":true}}"""
        val message = json.decodeFromString<ServerMessage>(serverJson)
        
        assertNotNull(message.serverContent)
        assertTrue(message.serverContent?.turnComplete == true)
    }
    
    @Test
    fun `ServerMessage with interrupted content deserializes`() {
        val serverJson = """{"serverContent":{"interrupted":true}}"""
        val message = json.decodeFromString<ServerMessage>(serverJson)
        
        assertTrue(message.serverContent?.interrupted == true)
    }
    
    @Test
    fun `Blob factory methods create correct types`() {
        val audio = Blob.audioPcm("data1")
        val image = Blob.imageJpeg("data2")
        
        assertEquals("audio/pcm", audio.mimeType)
        assertEquals("image/jpeg", image.mimeType)
    }
}
