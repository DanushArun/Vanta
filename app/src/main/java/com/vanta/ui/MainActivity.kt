package com.vanta.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.vanta.domain.coordinator.VantaCoordinator
import com.vanta.ui.theme.VantaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Main Activity - Zero UI design for accessibility.
 * 
 * The UI is intentionally minimal:
 * - Large status indicator with high contrast
 * - Full TalkBack support
 * - Audio feedback for all state changes
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var coordinator: VantaCoordinator
    
    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            startVanta()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            VantaTheme {
                val state by coordinator.state.collectAsState()
                VantaScreen(state = state)
            }
        }
        
        checkAndRequestPermissions()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        coordinator.stop()
    }
    
    private fun checkAndRequestPermissions() {
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isEmpty()) {
            startVanta()
        } else {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }
    
    private fun startVanta() {
        lifecycleScope.launch {
            coordinator.start(this@MainActivity)
        }
    }
}

/**
 * Zero-UI screen with accessibility-first design.
 */
@Composable
fun VantaScreen(state: VantaCoordinator.VantaState) {
    val backgroundColor = when (state) {
        is VantaCoordinator.VantaState.Idle -> Color.Black
        is VantaCoordinator.VantaState.Connecting -> Color(0xFF1A1A2E)
        is VantaCoordinator.VantaState.Listening -> Color(0xFF0F3460)
        is VantaCoordinator.VantaState.Speaking -> Color(0xFF16213E)
        is VantaCoordinator.VantaState.UserSpeaking -> Color(0xFF1B4332)  // Green tint when user speaks
        is VantaCoordinator.VantaState.Error -> Color(0xFF4A0000)
    }
    
    val statusText = when (state) {
        is VantaCoordinator.VantaState.Idle -> "Vanta Idle"
        is VantaCoordinator.VantaState.Connecting -> "Connecting..."
        is VantaCoordinator.VantaState.Listening -> "Listening"
        is VantaCoordinator.VantaState.Speaking -> "Speaking"
        is VantaCoordinator.VantaState.UserSpeaking -> "You're Speaking"
        is VantaCoordinator.VantaState.Error -> "Error: ${state.message}"
    }
    
    val accessibilityDescription = when (state) {
        is VantaCoordinator.VantaState.Idle -> "Vanta is idle. Waiting to start."
        is VantaCoordinator.VantaState.Connecting -> "Vanta is connecting. Please wait."
        is VantaCoordinator.VantaState.Listening -> "Vanta is ready. Speak to ask a question."
        is VantaCoordinator.VantaState.Speaking -> "Vanta is speaking. You can interrupt anytime."
        is VantaCoordinator.VantaState.UserSpeaking -> "You are speaking. Vanta is listening."
        is VantaCoordinator.VantaState.Error -> "Error occurred: ${state.message}"
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .semantics { contentDescription = accessibilityDescription },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Status indicator - large, high contrast
            Text(
                text = statusText,
                color = Color.White,
                fontSize = 32.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp)
            )
            
            // Pulse animation for active states
            if (state is VantaCoordinator.VantaState.Listening || 
                state is VantaCoordinator.VantaState.Speaking) {
                PulseIndicator(
                    isActive = state is VantaCoordinator.VantaState.Speaking
                )
            }
        }
    }
}

@Composable
fun PulseIndicator(isActive: Boolean) {
    val color = if (isActive) Color(0xFF4CAF50) else Color(0xFF2196F3)
    
    Box(
        modifier = Modifier
            .size(100.dp)
            .background(color.copy(alpha = 0.3f), shape = androidx.compose.foundation.shape.CircleShape)
            .padding(20.dp)
            .background(color.copy(alpha = 0.6f), shape = androidx.compose.foundation.shape.CircleShape)
            .padding(20.dp)
            .background(color, shape = androidx.compose.foundation.shape.CircleShape)
    )
}
