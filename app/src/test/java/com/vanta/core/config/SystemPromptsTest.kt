package com.vanta.core.config

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for SystemPrompts.
 */
class SystemPromptsTest {
    
    @Test
    fun `social mode prompt contains key instructions`() {
        val prompt = SystemPrompts.SOCIAL_MODE
        
        // Must mention focus on people
        assertTrue(prompt.contains("People", ignoreCase = true))
        assertTrue(prompt.contains("HUMAN DYNAMICS", ignoreCase = true))
        
        // Must mention clock directions
        assertTrue(prompt.contains("clock", ignoreCase = true))
        
        // Must mention exit alerts
        assertTrue(prompt.contains("leaves", ignoreCase = true) || 
                   prompt.contains("exit", ignoreCase = true))
    }
    
    @Test
    fun `mirror mode prompt contains appearance instructions`() {
        val prompt = SystemPrompts.MIRROR_MODE
        
        assertTrue(prompt.contains("appearance", ignoreCase = true))
        assertTrue(prompt.contains("honest", ignoreCase = true))
    }
    
    @Test
    fun `scene mode prompt contains environment instructions`() {
        val prompt = SystemPrompts.SCENE_MODE
        
        assertTrue(prompt.contains("environment", ignoreCase = true))
        assertTrue(prompt.contains("clock", ignoreCase = true))
    }
    
    @Test
    fun `forMode returns correct prompt for each mode`() {
        assertEquals(SystemPrompts.SOCIAL_MODE, SystemPrompts.forMode(VantaMode.SOCIAL))
        assertEquals(SystemPrompts.MIRROR_MODE, SystemPrompts.forMode(VantaMode.MIRROR))
        assertEquals(SystemPrompts.SCENE_MODE, SystemPrompts.forMode(VantaMode.SCENE))
    }
    
    @Test
    fun `all modes are covered`() {
        // Ensure no mode throws exception
        VantaMode.values().forEach { mode ->
            assertNotNull(SystemPrompts.forMode(mode))
        }
    }
}
