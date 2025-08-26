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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
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
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.suspendCancellableCoroutine
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("LogNotTimber")
class RtspPlayerActivity : ComponentActivity() {
    
    private var rtspSurfaceView: RtspSurfaceView? = null
    private var isConnected = false
    private var detector: OnnxCircleDetector? = null
    
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
        detector = OnnxCircleDetector(this)
        
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
        var overlayBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
        var overlaySize by remember { mutableStateOf(IntSize(0, 0)) }
        var imageScale by remember { mutableStateOf(1f) }
        var baseScale by remember { mutableStateOf(1f) }
        var imageOffset by remember { mutableStateOf(Offset.Zero) }
        var showOverlayImage by remember { mutableStateOf(false) } // 默认不显示图片
        var circles by remember { mutableStateOf(listOf<OnnxCircleDetector.Circle>()) }
        
        // UI状态管理
        val hasOverlayImage = overlayBitmap != null
        val showDetectionButton = !hasOverlayImage || !showOverlayImage
        val showHideImageButton = hasOverlayImage && showOverlayImage
        
        Box(modifier = Modifier.fillMaxSize()) {
            // RTSP Surface View with 16:9 aspect ratio
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
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .align(Alignment.Center)
            )
            
            // 覆盖图片和绘制层（覆盖整个屏幕，16:9比例，支持缩放拖拽）
            if (overlayBitmap != null) {
                val bm = overlayBitmap!!
                val screenW = remember { mutableStateOf(0) }
                val screenH = remember { mutableStateOf(0) }
                val density = LocalDensity.current
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coords ->
                            val sz = coords.size
                            if (screenW.value != sz.width || screenH.value != sz.height) {
                                screenW.value = sz.width
                                screenH.value = sz.height
                                // 计算覆盖整个屏幕的缩放比例
                                val bw = bm.width
                                val bh = bm.height
                                if (bw > 0 && bh > 0) {
                                    val scaleCover = maxOf(
                                        sz.width.toFloat() / bw.toFloat(),
                                        sz.height.toFloat() / bh.toFloat()
                                    )
                                    baseScale = scaleCover
                                    if (imageScale <= 1f) {
                                        imageScale = scaleCover // 只在初始化或重置时设置
                                    }
                                    overlaySize = IntSize(bw, bh)
                                }
                            }
                        }
                        .clipToBounds()
                ) {
                    // 图片层 - 只在showOverlayImage为true时显示
                    if (showOverlayImage) {
                        val widthDp = with(density) { overlaySize.width.toDp() }
                        val heightDp = with(density) { overlaySize.height.toDp() }
                        Image(
                            bitmap = bm,
                            contentDescription = "overlay",
                            contentScale = ContentScale.None,
                            modifier = Modifier
                                .size(widthDp, heightDp)
                                .graphicsLayer(
                                    scaleX = imageScale,
                                    scaleY = imageScale,
                                    translationX = imageOffset.x,
                                    translationY = imageOffset.y
                                )
                        )
                    }

                    // 绘制层 - 总是显示（跟随图片变换，即使图片隐藏了也保留圆形绘制）
                    val widthDp = with(density) { overlaySize.width.toDp() }
                    val heightDp = with(density) { overlaySize.height.toDp() }
                    Canvas(
                        modifier = Modifier
                            .size(widthDp, heightDp)
                            .graphicsLayer(
                                scaleX = imageScale,
                                scaleY = imageScale,
                                translationX = imageOffset.x,
                                translationY = imageOffset.y
                            )
                            .pointerInput(showOverlayImage) {
                                // 只在图片显示时启用手势
                                if (showOverlayImage) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val minScale = baseScale
                                        val maxScale = baseScale * 3f
                                        val newScale = (imageScale * zoom).coerceIn(minScale, maxScale)
                                        imageScale = newScale
                                        imageOffset += pan
                                    }
                                }
                            }
                    ) {
                        // 绘制圆形 - 在图像坐标空间中绘制，这样它们会一起变换
                        val strokeWidth = 3f / imageScale  // 根据缩放调整线条宽度
                        val stroke = Stroke(width = strokeWidth)
                        circles.forEach { c ->
                            // 根据类别选择颜色
                            val circleColor = when (c.className) {
                                "RedCenter" -> Color.Red
                                "ROI" -> Color.Green
                                else -> Color.White
                            }
                            
                            // 绘制外圆
                            drawCircle(
                                color = circleColor,
                                radius = c.r,
                                center = Offset(c.cx, c.cy),
                                style = stroke
                            )
                            
                            // 绘制中心点
                            drawCircle(
                                color = circleColor,
                                radius = 6f / imageScale, // 根据缩放调整中心点大小
                                center = Offset(c.cx, c.cy)
                            )
                            
                            // 绘制中心十字标记，提高可见性
                            val crossSize = 15f / imageScale
                            // 水平线
                            drawLine(
                                color = circleColor,
                                start = Offset(c.cx - crossSize, c.cy),
                                end = Offset(c.cx + crossSize, c.cy),
                                strokeWidth = strokeWidth
                            )
                            // 垂直线  
                            drawLine(
                                color = circleColor,
                                start = Offset(c.cx, c.cy - crossSize),
                                end = Offset(c.cx, c.cy + crossSize),
                                strokeWidth = strokeWidth
                            )
                        }
                    }
                }
            }

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

            // 圆形检测按钮 - 只在适当时机显示
            if (showDetectionButton) {
                FloatingActionButton(
                    onClick = {
                        captureFrameBitmap { bmp ->
                            if (bmp != null) {
                                overlayBitmap = bmp.asImageBitmap()
                                showOverlayImage = true // 显示截取的图片
                                
                                // 重置缩放和偏移，确保图片以正确尺寸显示
                                imageScale = 1f
                                baseScale = 1f
                                imageOffset = Offset.Zero
                                
                                // Run model to detect circles
                                Log.i(TAG, "开始运行圆形检测...")
                                val list = detector?.detect(bmp) ?: emptyList()
                                circles = list
                                Log.i(TAG, "检测完成，找到 ${list.size} 个圆形目标")
                                list.forEach { circle ->
                                    Log.i(TAG, "检测结果: ${circle.className} - 中心(${circle.cx.toInt()}, ${circle.cy.toInt()}) 半径=${circle.r.toInt()} 置信度=${String.format("%.3f", circle.confidence)}")
                                }
                            } else {
                                Toast.makeText(context, "覆盖截图失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(24.dp),
                    containerColor = MaterialTheme.colorScheme.secondary
                ) {
                    Text(
                        text = "圆形检测",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // 隐藏图片按钮 - 只在图片显示时出现
            if (showHideImageButton) {
                FloatingActionButton(
                    onClick = { 
                        showOverlayImage = false // 隐藏图片但保留圆形绘制
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                    containerColor = MaterialTheme.colorScheme.tertiary
                ) {
                    Text(
                        text = "隐藏图片",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
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
                val w = if (surfaceView.width > 0) surfaceView.width else 1920
                val h = if (surfaceView.height > 0) surfaceView.height else 1080
                val surfaceBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
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

    private fun captureFrameBitmap(onResult: (Bitmap?) -> Unit) {
        rtspSurfaceView?.let { surfaceView ->
            if (!isConnected) {
                onResult(null)
                return
            }

            try {
                val w = if (surfaceView.width > 0) surfaceView.width else 1920
                val h = if (surfaceView.height > 0) surfaceView.height else 1080
                val surfaceBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val lock = Object()
                val success = AtomicBoolean(false)
                val thread = HandlerThread("OverlayCapture")
                thread.start()
                val handler = Handler(thread.looper)

                val listener = PixelCopy.OnPixelCopyFinishedListener { copyResult ->
                    success.set(copyResult == PixelCopy.SUCCESS)
                    synchronized(lock) { lock.notify() }
                }

                synchronized(lock) {
                    PixelCopy.request(
                        surfaceView.holder.surface,
                        surfaceBitmap,
                        listener,
                        handler
                    )
                    lock.wait(3000)
                }
                thread.quitSafely()
                if (success.get()) onResult(surfaceBitmap) else onResult(null)
            } catch (t: Throwable) {
                Log.e(TAG, "captureFrameBitmap error", t)
                onResult(null)
            }
        } ?: onResult(null)
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
        detector?.close()
        detector = null
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