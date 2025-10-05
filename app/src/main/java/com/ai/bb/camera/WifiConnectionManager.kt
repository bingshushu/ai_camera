package com.ai.bb.camera

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi

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
     */
    fun scanForIPCAMNetwork(onResult: (String?) -> Unit) {
        if (!wifiManager.isWifiEnabled) {
            onResult(null)
            return
        }

        // 开始扫描
        val scanSuccess = wifiManager.startScan()
        if (!scanSuccess) {
            Log.e(TAG, "Wi-Fi scan failed to start")
            onResult(null)
            return
        }

        // 获取扫描结果
        val scanResults = wifiManager.scanResults
        val ipcamNetwork = scanResults.firstOrNull {
            SSID_PATTERN.matches(it.SSID)
        }

        if (ipcamNetwork != null) {
            Log.i(TAG, "Found IPCAM network: ${ipcamNetwork.SSID}")
            onResult(ipcamNetwork.SSID)
        } else {
            Log.i(TAG, "No IPCAM network found in scan results")
            onResult(null)
        }
    }

    /**
     * 连接到指定的Wi-Fi网络
     * Android 10+ 使用新的API
     */
    fun connectToNetwork(ssid: String, onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectToNetworkModern(ssid, onResult)
        } else {
            @Suppress("DEPRECATION")
            connectToNetworkLegacy(ssid, onResult)
        }
    }

    /**
     * Android 10+ 的连接方法
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun connectToNetworkModern(ssid: String, onResult: (Boolean) -> Unit) {
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .setWpa2Passphrase(IPCAM_PASSWORD)
            .build()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .setNetworkSpecifier(specifier)
            .build()

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                Log.i(TAG, "Successfully connected to $ssid")
                connectivityManager.unregisterNetworkCallback(this)
                onResult(true)
            }

            override fun onUnavailable() {
                super.onUnavailable()
                Log.e(TAG, "Failed to connect to $ssid")
                connectivityManager.unregisterNetworkCallback(this)
                onResult(false)
            }
        }

        try {
            connectivityManager.requestNetwork(request, networkCallback, 30000)
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting network", e)
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
}
