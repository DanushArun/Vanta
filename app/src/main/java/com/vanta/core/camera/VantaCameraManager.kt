package com.vanta.core.camera

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.vanta.core.common.DispatcherProvider
import com.vanta.core.common.VantaLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages CameraX for capturing frames to send to Gemini.
 * 
 * Configuration:
 * - Front camera (for Social Mode - seeing who user faces)
 * - VGA resolution (640x480) - sufficient for faces, saves bandwidth
 * - Adaptive FPS based on conversation state
 */
@Singleton
class VantaCameraManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider
) {
    companion object {
        private const val TAG = "VantaCameraManager"
        private const val TARGET_WIDTH = 640
        private const val TARGET_HEIGHT = 480
        private const val JPEG_QUALITY = 50
        private const val DEFAULT_FRAME_INTERVAL_MS = 500L  // 2 FPS
    }
    
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    
    // Frame output as JPEG bytes
    private val _frames = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val frames: SharedFlow<ByteArray> = _frames.asSharedFlow()
    
    // Configuration
    private var frameIntervalMs = DEFAULT_FRAME_INTERVAL_MS
    private var lastFrameTime = 0L
    private var isActive = false
    
    /**
     * Start the camera and begin emitting frames.
     */
    suspend fun start(lifecycleOwner: LifecycleOwner) = withContext(dispatchers.main) {
        if (isActive) return@withContext
        
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(lifecycleOwner)
                isActive = true
                VantaLogger.i("Camera started", tag = TAG)
            } catch (e: Exception) {
                VantaLogger.e("Failed to start camera", e, tag = TAG)
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    /**
     * Stop the camera.
     */
    fun stop() {
        cameraProvider?.unbindAll()
        isActive = false
        VantaLogger.i("Camera stopped", tag = TAG)
    }
    
    /**
     * Set frame rate.
     * @param fps Frames per second (1-4 supported)
     */
    fun setFrameRate(fps: Int) {
        frameIntervalMs = (1000L / fps.coerceIn(1, 4))
        VantaLogger.d("Frame rate set to $fps FPS", tag = TAG)
    }
    
    private fun bindCameraUseCases(lifecycleOwner: LifecycleOwner) {
        val cameraProvider = cameraProvider ?: return
        
        // Use front camera for Social Mode
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()
        
        // Configure image analysis
        imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(TARGET_WIDTH, TARGET_HEIGHT))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .apply {
                setAnalyzer(cameraExecutor) { imageProxy ->
                    processFrame(imageProxy)
                }
            }
        
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                imageAnalysis
            )
        } catch (e: Exception) {
            VantaLogger.e("Failed to bind camera use cases", e, tag = TAG)
        }
    }
    
    private fun processFrame(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        
        // Throttle frame rate
        if (currentTime - lastFrameTime < frameIntervalMs) {
            imageProxy.close()
            return
        }
        lastFrameTime = currentTime
        
        // Convert YUV to JPEG on background thread
        scope.launch {
            try {
                val jpegBytes = convertToJpeg(imageProxy)
                _frames.emit(jpegBytes)
            } catch (e: Exception) {
                VantaLogger.e("Frame conversion failed", e, tag = TAG)
            } finally {
                imageProxy.close()
            }
        }
    }
    
    private suspend fun convertToJpeg(imageProxy: ImageProxy): ByteArray = 
        withContext(dispatchers.default) {
            val yBuffer = imageProxy.planes[0].buffer
            val uBuffer = imageProxy.planes[1].buffer
            val vBuffer = imageProxy.planes[2].buffer
            
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            
            val nv21 = ByteArray(ySize + uSize + vSize)
            
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)
            
            val yuvImage = YuvImage(
                nv21,
                ImageFormat.NV21,
                imageProxy.width,
                imageProxy.height,
                null
            )
            
            ByteArrayOutputStream().use { output ->
                yuvImage.compressToJpeg(
                    Rect(0, 0, imageProxy.width, imageProxy.height),
                    JPEG_QUALITY,
                    output
                )
                output.toByteArray()
            }
        }
    
    fun release() {
        stop()
        cameraExecutor.shutdown()
        scope.cancel()
    }
}
