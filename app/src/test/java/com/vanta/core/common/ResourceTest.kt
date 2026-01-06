package com.vanta.core.common

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Resource wrapper class.
 */
class ResourceTest {
    
    @Test
    fun `success creates Success instance with data`() {
        val data = "test data"
        val result = Resource.success(data)
        
        assertTrue(result.isSuccess)
        assertFalse(result.isError)
        assertFalse(result.isLoading)
        assertEquals(data, result.getOrNull())
    }
    
    @Test
    fun `error creates Error instance with message`() {
        val message = "Something went wrong"
        val result = Resource.error(message)
        
        assertFalse(result.isSuccess)
        assertTrue(result.isError)
        assertNull(result.getOrNull())
        
        val error = result as Resource.Error
        assertEquals(message, error.message)
    }
    
    @Test
    fun `map transforms Success data`() {
        val original = Resource.success(10)
        val mapped = original.map { it * 2 }
        
        assertTrue(mapped.isSuccess)
        assertEquals(20, mapped.getOrNull())
    }
    
    @Test
    fun `map does not transform Error`() {
        val original: Resource<Int> = Resource.error("error")
        val mapped = original.map { it * 2 }
        
        assertTrue(mapped.isError)
    }
    
    @Test
    fun `loading state is correct`() {
        val result = Resource.loading()
        
        assertTrue(result.isLoading)
        assertFalse(result.isSuccess)
        assertFalse(result.isError)
    }
}
