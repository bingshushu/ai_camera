package com.ai.bb.camera

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

class WifiConnectionManager(private val context: Context) {

    companion object {
        private const val TAG = "WifiConnectionManager"
        private const val IPCAM_SSID_PREFIX = "IPCAM-"
        private const val IPCAM_PASSWORD = "01234567"
        private val SSID_PATTERN = Regex("^IPCAM-\\d{6}$")
    }

    private val wifiManager: WifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private var scanReceiver: BroadcastReceiver? = null
    private var scanCallback: ((String?) -> Unit)? = null
    private var isProcessingScan = false  // 防止重复处理扫描结果
    
    private var wifiStateReceiver: BroadcastReceiver? = null
    private var wifiStateCallback: ((Boolean) -> Unit)? = null
    private var targetSsid: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    /**
     * 检查是否有Wi-Fi扫描所需的权限
     */
    fun hasRequiredPermissions(): Boolean {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )
        
        // Android 13+ 需要额外的权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 获取需要请求的权限列表
     */
    fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        
        return permissions.toTypedArray()
    }

    /**
     * 检查Wi-Fi是否已启用
     */
    fun isWifiEnabled(): Boolean {
        return wifiManager.isWifiEnabled
    }

    /**
     * 检查是否已连接到Wi-Fi
     */
    fun isConnectedToWifi(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * 获取当前连接的Wi-Fi SSID
     */
    fun getCurrentSsid(): String? {
        if (!isConnectedToWifi()) return null

        val wifiInfo = wifiManager.connectionInfo ?: return null
        var ssid = wifiInfo.ssid

        // 移除SSID两端的引号
        if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length - 1)
        }

        return ssid
    }

    /**
     * 检查当前SSID是否匹配IPCAM格式
     */
    fun isCurrentSsidMatchIPCAM(): Boolean {
        val ssid = getCurrentSsid() ?: return false
        return SSID_PATTERN.matches(ssid)
    }

    /**
     * 扫描可用的Wi-Fi网络并查找匹配IPCAM格式的SSID
     * 使用BroadcastReceiver来接收扫描结果(适配新版Android)
     */
    fun scanForIPCAMNetwork(onResult: (String?) -> Unit) {
        Log.d(TAG, "scanForIPCAMNetwork called")
        
        // 检查权限
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Missing required permissions for Wi-Fi scanning")
            onResult(null)
            return
        }
        
        if (!wifiManager.isWifiEnabled) {
            Log.e(TAG, "Wi-Fi is not enabled")
            onResult(null)
            return
        }

        // 先清理旧的接收器（但不清空callback，因为还没保存新的）
        scanReceiver?.let {
            try {
                context.unregisterReceiver(it)
                Log.d(TAG, "Old scan receiver unregistered")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister old scan receiver", e)
            }
            scanReceiver = null
        }
        
        // 保存新的回调
        scanCallback = onResult
        isProcessingScan = false  // 重置处理标志
        Log.d(TAG, "Callback saved, isProcessingScan reset to false")
        
        scanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "BroadcastReceiver onReceive called, action: ${intent?.action}")
                when (intent?.action) {
                    WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> {
                        // 防止重复处理
                        if (isProcessingScan) {
                            Log.w(TAG, "Already processing scan results, ignoring duplicate broadcast")
                            return
                        }
                        
                        Log.d(TAG, "Setting isProcessingScan = true")
                        isProcessingScan = true
                        
                        val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                        Log.d(TAG, "Scan results updated: $success")
                        if (success) {
                            processScanResults()
                        } else {
                            Log.e(TAG, "Wi-Fi scan failed")
                            scanCallback?.invoke(null)
                            unregisterScanReceiver()
                        }
                    }
                }
            }
        }

        // 注册接收器
        val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(scanReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(scanReceiver, intentFilter)
        }

        // 开始扫描
        val scanStarted = wifiManager.startScan()
        if (!scanStarted) {
            Log.e(TAG, "Failed to start Wi-Fi scan (may be throttled)")
            // 标记正在处理，防止BroadcastReceiver重复处理
            isProcessingScan = true
            // 即使startScan失败,也尝试获取缓存的扫描结果
            processScanResults()
        } else {
            Log.i(TAG, "Wi-Fi scan initiated successfully")
        }
    }

    /**
     * 处理扫描结果
     */
    @Synchronized
    private fun processScanResults() {
        // 在方法开始时就保存callback引用，避免并发问题
        val callback = scanCallback
        
        try {
            if (callback == null) {
                Log.e(TAG, "scanCallback is null at start of processScanResults!")
                return
            }
            
            val scanResults = wifiManager.scanResults
            Log.i(TAG, "Processing ${scanResults.size} scan results")
            
            // 查找匹配IPCAM格式的网络
            val ipcamNetwork = scanResults.firstOrNull {
                val ssid = it.SSID
                val matches = SSID_PATTERN.matches(ssid)
                Log.d(TAG, "Checking SSID: $ssid, matches: $matches")
                matches
            }

            if (ipcamNetwork != null) {
                Log.i(TAG, "Found IPCAM network: ${ipcamNetwork.SSID}")
                Log.d(TAG, "Invoking callback with SSID: ${ipcamNetwork.SSID}")
                callback.invoke(ipcamNetwork.SSID)
            } else {
                Log.i(TAG, "No IPCAM network found in scan results")
                callback.invoke(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing scan results", e)
            callback?.invoke(null)
        } finally {
            unregisterScanReceiver()
        }
    }

    /**
     * 注销扫描接收器
     */
    private fun unregisterScanReceiver() {
        scanReceiver?.let {
            try {
                context.unregisterReceiver(it)
                Log.d(TAG, "Scan receiver unregistered")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister scan receiver", e)
            }
            scanReceiver = null
        }
        scanCallback = null
        isProcessingScan = false  // 重置处理标志
    }

    /**
     * 连接到指定的Wi-Fi网络
     * Android 10+ 使用新的API
     */
    fun connectToNetwork(ssid: String, onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 需要监听连接状态
            startMonitoringWifiConnection(ssid, onResult)
            connectToNetworkModern(ssid, onResult)
        } else {
            @Suppress("DEPRECATION")
            connectToNetworkLegacy(ssid, onResult)
        }
    }
    
    /**
     * 开始监听Wi-Fi连接状态（Android 10+）
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun startMonitoringWifiConnection(ssid: String, onConnected: (Boolean) -> Unit) {
        targetSsid = ssid
        wifiStateCallback = onConnected
        
        // 先清理旧的监听器
        wifiStateReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister wifi state receiver", e)
            }
        }
        
        wifiStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                        Log.d(TAG, "Wi-Fi network state changed")
                        val currentSsid = getCurrentSsid()
                        Log.d(TAG, "Current SSID: $currentSsid, Target SSID: $targetSsid")
                        
                        if (currentSsid == targetSsid) {
                            Log.i(TAG, "Successfully connected to target Wi-Fi: $targetSsid")
                            wifiStateCallback?.invoke(true)
                            stopMonitoringWifiConnection()
                        } else if (currentSsid != null && currentSsid != targetSsid) {
                            // 连接到了其他Wi-Fi（非目标Wi-Fi）
                            Log.w(TAG, "Connected to wrong Wi-Fi: $currentSsid (expected: $targetSsid)")
                            wifiStateCallback?.invoke(false)
                            stopMonitoringWifiConnection()
                        }
                    }
                }
            }
        }
        
        val filter = IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(wifiStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(wifiStateReceiver, filter)
        }
        
        // 设置60秒超时
        timeoutRunnable = Runnable {
            Log.w(TAG, "Wi-Fi connection timeout after 60 seconds")
            wifiStateCallback?.invoke(false)
            stopMonitoringWifiConnection()
        }
        handler.postDelayed(timeoutRunnable!!, 60000) // 60秒超时
        
        Log.d(TAG, "Started monitoring Wi-Fi connection for $ssid (60s timeout)")
    }
    
    /**
     * 停止监听Wi-Fi连接状态
     */
    private fun stopMonitoringWifiConnection() {
        // 取消超时计时器
        timeoutRunnable?.let {
            handler.removeCallbacks(it)
            timeoutRunnable = null
        }
        
        wifiStateReceiver?.let {
            try {
                context.unregisterReceiver(it)
                Log.d(TAG, "Stopped monitoring Wi-Fi connection")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister wifi state receiver", e)
            }
            wifiStateReceiver = null
        }
        wifiStateCallback = null
        targetSsid = null
    }

    /**
     * Android 10+ 的连接方法
     * Android 10+不允许应用直接切换Wi-Fi，需要使用WifiNetworkSuggestion并引导用户连接
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun connectToNetworkModern(ssid: String, onResult: (Boolean) -> Unit) {
        try {
            // 创建网络建议
            val suggestion = WifiNetworkSuggestion.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(IPCAM_PASSWORD)
                .setIsAppInteractionRequired(true) // 需要用户交互
                .setPriority(Int.MAX_VALUE) // 设置最高优先级
                .build()

            val suggestionsList = listOf(suggestion)
            
            // 添加网络建议
            val status = wifiManager.addNetworkSuggestions(suggestionsList)
            
            when (status) {
                WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS -> {
                    Log.i(TAG, "Network suggestion added successfully for $ssid")
                    
                    // 打开Wi-Fi设置面板，让用户手动连接
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            val panelIntent = Intent(Settings.Panel.ACTION_WIFI)
                            panelIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(panelIntent)
                            Log.i(TAG, "Opened Wi-Fi settings panel")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to open Wi-Fi panel, opening settings", e)
                            openWifiSettings()
                        }
                    } else {
                        openWifiSettings()
                    }
                    
                    // 注意：不在这里调用onResult，等待监听器检测到真实连接后再调用
                    Log.i(TAG, "Wi-Fi settings panel opened, waiting for user to connect")
                }
                WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE -> {
                    Log.w(TAG, "Network suggestion already exists for $ssid")
                    // 建议已存在，打开设置让用户连接
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val panelIntent = Intent(Settings.Panel.ACTION_WIFI)
                        panelIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(panelIntent)
                    } else {
                        openWifiSettings()
                    }
                    
                    // 不在这里调用onResult，等待监听器检测到真实连接
                    Log.i(TAG, "Wi-Fi settings panel opened, waiting for user to connect")
                }
                else -> {
                    Log.e(TAG, "Failed to add network suggestion, status: $status")
                    // 添加失败，直接打开Wi-Fi设置
                    openWifiSettings()
                    onResult(false)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in connectToNetworkModern", e)
            // 发生异常，打开Wi-Fi设置
            openWifiSettings()
            onResult(false)
        }
    }

    /**
     * Android 9 及以下的连接方法（已弃用但仍需支持）
     */
    @Suppress("DEPRECATION")
    private fun connectToNetworkLegacy(ssid: String, onResult: (Boolean) -> Unit) {
        try {
            val wifiConfig = WifiConfiguration().apply {
                SSID = "\"$ssid\""
                preSharedKey = "\"$IPCAM_PASSWORD\""
            }

            val netId = wifiManager.addNetwork(wifiConfig)
            if (netId == -1) {
                Log.e(TAG, "Failed to add network configuration")
                onResult(false)
                return
            }

            wifiManager.disconnect()
            val enabled = wifiManager.enableNetwork(netId, true)
            val reconnected = wifiManager.reconnect()

            if (enabled && reconnected) {
                Log.i(TAG, "Initiated connection to $ssid")
                // 注意：实际连接是异步的，这里只是发起了连接请求
                // 在实际应用中可能需要监听连接状态变化
                onResult(true)
            } else {
                Log.e(TAG, "Failed to connect to $ssid")
                onResult(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to network (legacy)", e)
            onResult(false)
        }
    }

    /**
     * 打开系统Wi-Fi设置页面
     */
    fun openWifiSettings() {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
    
    /**
     * 检查是否正在监听Wi-Fi连接
     */
    fun isMonitoringConnection(): Boolean {
        return wifiStateReceiver != null
    }
    
    /**
     * 获取当前监听的目标SSID
     */
    fun getTargetSsid(): String? {
        return targetSsid
    }
    
    /**
     * 清理资源(在Activity销毁时调用)
     */
    fun cleanup() {
        unregisterScanReceiver()
        stopMonitoringWifiConnection()
    }
}
