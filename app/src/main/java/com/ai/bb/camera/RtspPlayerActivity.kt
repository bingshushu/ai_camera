package com.ai.bb.camera

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.view.PixelCopy
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.alexvas.rtsp.widget.RtspDataListener
import com.alexvas.rtsp.widget.RtspStatusListener
import com.alexvas.rtsp.widget.RtspSurfaceView
import com.ai.bb.camera.ui.theme.AICameraTheme
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("LogNotTimber")
class RtspPlayerActivity : ComponentActivity() {
    
    private var rtspSurfaceView: RtspSurfaceView? = null
    private var isConnected = false
    
    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            Toast.makeText(this, "Storage permission is required to save screenshots", Toast.LENGTH_LONG).show()
        }
    }
    
    companion object {
        private const val TAG = "RtspPlayerActivity"
        private const val DEFAULT_RTSP_URL = "rtsp://192.168.1.88/11"
        private const val DEFAULT_USERNAME = "admin"
        private const val DEFAULT_PASSWORD = "admin"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set fullscreen and landscape mode
        setupFullscreenLandscape()
        
        setContent {
            AICameraTheme {
                RtspPlayerScreen()
            }
        }
    }
    
    private fun setupFullscreenLandscape() {
        // Force landscape orientation
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        
        // Enable fullscreen mode
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        
        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Set background color to black
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
    }
    
    @Composable
    fun RtspPlayerScreen() {
        var isLoading by remember { mutableStateOf(false) }
        var statusText by remember { mutableStateOf("Disconnected") }
        val context = LocalContext.current
        
        Box(modifier = Modifier.fillMaxSize()) {
            // RTSP Surface View
            AndroidView(
                factory = { ctx ->
                    RtspSurfaceView(ctx).apply {
                        rtspSurfaceView = this
                        
                        // Set up status listener
                        setStatusListener(object : RtspStatusListener {
                            override fun onRtspStatusConnecting() {
                                Log.d(TAG, "RTSP Connecting...")
                                isLoading = true
                                statusText = "Connecting..."
                            }
                            
                            override fun onRtspStatusConnected() {
                                Log.d(TAG, "RTSP Connected")
                                isLoading = false
                                statusText = "Connected"
                                isConnected = true
                            }
                            
                            override fun onRtspStatusDisconnecting() {
                                Log.d(TAG, "RTSP Disconnecting...")
                                statusText = "Disconnecting..."
                            }
                            
                            override fun onRtspStatusDisconnected() {
                                Log.d(TAG, "RTSP Disconnected")
                                isLoading = false
                                statusText = "Disconnected"
                                isConnected = false
                            }
                            
                            override fun onRtspStatusFailedUnauthorized() {
                                Log.e(TAG, "RTSP Failed: Unauthorized")
                                isLoading = false
                                statusText = "Authentication Failed"
                                isConnected = false
                            }
                            
                            override fun onRtspStatusFailed(message: String?) {
                                Log.e(TAG, "RTSP Failed: $message")
                                isLoading = false
                                statusText = "Error: $message"
                                isConnected = false
                            }
                            
                            override fun onRtspFirstFrameRendered() {
                                Log.i(TAG, "First frame rendered")
                                statusText = "Playing"
                            }
                            
                            override fun onRtspFrameSizeChanged(width: Int, height: Int) {
                                Log.i(TAG, "Frame size changed: ${width}x${height}")
                            }
                        })
                        
                        // Set up data listener
                        setDataListener(object : RtspDataListener {
                            override fun onRtspDataApplicationDataReceived(
                                data: ByteArray,
                                offset: Int,
                                length: Int,
                                timestamp: Long
                            ) {
                                // Handle application data if needed
                            }
                        })
                        
                        // Start RTSP connection
                        val uri = Uri.parse(DEFAULT_RTSP_URL)
                        init(uri, DEFAULT_USERNAME, DEFAULT_PASSWORD, "AICamera-RTSP-Client")
                        start(requestVideo = true, requestAudio = true, requestApplication = false)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            
            // Loading indicator
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }
            
            // Status text
            Text(
                text = statusText,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            )
            
            // Floating screenshot button
            FloatingActionButton(
                onClick = { takeScreenshot() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    text = "截图",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
    
    private fun takeScreenshot() {
        rtspSurfaceView?.let { surfaceView ->
            if (!isConnected) {
                Toast.makeText(this, "RTSP not connected", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Check permissions first
            if (!checkStoragePermissions()) {
                requestStoragePermissions()
                return
            }
            
            try {
                // Create bitmap with surface dimensions
                val surfaceBitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
                val lock = Object()
                val success = AtomicBoolean(false)
                val thread = HandlerThread("ScreenshotHelper")
                thread.start()
                val handler = Handler(thread.looper)
                
                val listener = PixelCopy.OnPixelCopyFinishedListener { copyResult ->
                    success.set(copyResult == PixelCopy.SUCCESS)
                    synchronized(lock) {
                        lock.notify()
                    }
                }
                
                synchronized(lock) {
                    PixelCopy.request(
                        surfaceView.holder.surface,
                        surfaceBitmap,
                        listener,
                        handler
                    )
                    lock.wait(3000) // Wait max 3 seconds
                }
                
                thread.quitSafely()
                
                if (success.get()) {
                    // Save bitmap to gallery
                    saveBitmapToGallery(surfaceBitmap)
                } else {
                    Toast.makeText(this, "Screenshot failed", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "Screenshot failed")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error taking screenshot", e)
                Toast.makeText(this, "Screenshot error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(this, "Surface not available", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun checkStoragePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun requestStoragePermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
        requestPermissionLauncher.launch(permissions)
    }
    
    private fun saveBitmapToGallery(bitmap: Bitmap) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "RTSP_Screenshot_$timestamp.jpg"
            
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AICamera")
                }
            }
            
            val contentResolver = contentResolver
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            
            uri?.let { imageUri ->
                val outputStream: OutputStream? = contentResolver.openOutputStream(imageUri)
                outputStream?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                }
                Toast.makeText(this, "Screenshot saved to gallery", Toast.LENGTH_SHORT).show()
                Log.i(TAG, "Screenshot saved successfully to: $imageUri")
            } ?: run {
                Toast.makeText(this, "Failed to create image file", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Failed to create image URI")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving screenshot to gallery", e)
            Toast.makeText(this, "Failed to save screenshot: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        rtspSurfaceView?.stop()
        rtspSurfaceView = null
    }
    
    override fun onPause() {
        super.onPause()
        rtspSurfaceView?.takeIf { it.isStarted() }?.stop()
    }
    
    override fun onResume() {
        super.onResume()
        rtspSurfaceView?.takeIf { !it.isStarted() }?.let {
            val uri = Uri.parse(DEFAULT_RTSP_URL)
            it.init(uri, DEFAULT_USERNAME, DEFAULT_PASSWORD, "AICamera-RTSP-Client")
            it.start(requestVideo = true, requestAudio = true, requestApplication = false)
        }
    }
}