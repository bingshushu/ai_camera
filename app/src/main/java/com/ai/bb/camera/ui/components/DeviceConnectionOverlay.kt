package com.ai.bb.camera.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.bb.camera.R
import kotlinx.coroutines.delay

enum class ConnectionStatus {
    NOT_STARTED,
    CHECKING_WIFI,
    WIFI_NOT_CONNECTED,
    CONNECTING_RTSP,
    RTSP_FAILED,
    WIFI_MISMATCH,
    SCANNING_WIFI,
    CONNECTING_WIFI,
    WIFI_CONNECTION_FAILED,
    CONNECTED,
    ASK_OPEN_SETTINGS
}

@Composable
fun getStatusText(status: ConnectionStatus): String {
    return when (status) {
        ConnectionStatus.NOT_STARTED -> stringResource(R.string.device_not_connected)
        ConnectionStatus.CHECKING_WIFI -> stringResource(R.string.checking_wifi)
        ConnectionStatus.WIFI_NOT_CONNECTED -> stringResource(R.string.wifi_not_connected)
        ConnectionStatus.CONNECTING_RTSP -> stringResource(R.string.connecting_rtsp)
        ConnectionStatus.RTSP_FAILED -> stringResource(R.string.rtsp_connection_failed)
        ConnectionStatus.WIFI_MISMATCH -> stringResource(R.string.wifi_mismatch)
        ConnectionStatus.SCANNING_WIFI -> stringResource(R.string.scanning_wifi)
        ConnectionStatus.CONNECTING_WIFI -> stringResource(R.string.connecting_wifi)
        ConnectionStatus.WIFI_CONNECTION_FAILED -> stringResource(R.string.wifi_connection_failed)
        ConnectionStatus.CONNECTED -> stringResource(R.string.device_connected)
        ConnectionStatus.ASK_OPEN_SETTINGS -> stringResource(R.string.wifi_not_found)
    }
}

@Composable
fun getErrorMessage(status: ConnectionStatus): String {
    return when (status) {
        ConnectionStatus.WIFI_NOT_CONNECTED -> stringResource(R.string.error_wifi_not_connected)
        ConnectionStatus.RTSP_FAILED -> stringResource(R.string.error_rtsp_failed)
        ConnectionStatus.WIFI_MISMATCH -> stringResource(R.string.error_wifi_mismatch)
        ConnectionStatus.WIFI_CONNECTION_FAILED -> stringResource(R.string.error_wifi_connection_failed)
        ConnectionStatus.ASK_OPEN_SETTINGS -> stringResource(R.string.error_wifi_not_found)
        else -> ""
    }
}

@Composable
fun DeviceConnectionOverlay(
    status: ConnectionStatus,
    onConnectClick: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    onDismiss: () -> Unit,
    onResetStatus: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 循环显示的等待提示文字
    var waitingMessageIndex by remember { mutableStateOf(0) }
    val waitingMessages = listOf(
        stringResource(R.string.device_connecting_wait),
        stringResource(R.string.device_keep_on)
    )

    // 显示Wi-Fi未连接对话框
    var showNoWifiDialog by remember { mutableStateOf(false) }

    // 切换等待消息
    LaunchedEffect(status) {
        if (status == ConnectionStatus.CHECKING_WIFI ||
            status == ConnectionStatus.CONNECTING_RTSP ||
            status == ConnectionStatus.SCANNING_WIFI ||
            status == ConnectionStatus.CONNECTING_WIFI
        ) {
            while (true) {
                delay(3000)
                waitingMessageIndex = (waitingMessageIndex + 1) % waitingMessages.size
            }
        }
    }

    // 假进度条动画
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(status) {
        if (status == ConnectionStatus.CHECKING_WIFI ||
            status == ConnectionStatus.CONNECTING_RTSP ||
            status == ConnectionStatus.SCANNING_WIFI ||
            status == ConnectionStatus.CONNECTING_WIFI
        ) {
            progress = 0f
            while (progress < 1f) {
                delay(100)
                progress += 0.01f
            }
        } else {
            progress = 0f
        }
    }

    // 当状态为Wi-Fi未连接时显示对话框
    LaunchedEffect(status) {
        if (status == ConnectionStatus.WIFI_NOT_CONNECTED) {
            showNoWifiDialog = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // 关闭按钮 - 右上角
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        // 左右布局
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalArrangement = Arrangement.spacedBy(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：产品图片
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.mipmap.product),
                    contentDescription = stringResource(R.string.product_image),
                    modifier = Modifier.fillMaxWidth(0.8f)
                )
            }

            // 右侧：文字和按钮
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
            ) {

                // 连接状态文字
                Text(
                    text = getStatusText(status),
                    color = Color.White,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )

                // 进度条和等待提示
                when (status) {
                    ConnectionStatus.CHECKING_WIFI,
                    ConnectionStatus.CONNECTING_RTSP,
                    ConnectionStatus.SCANNING_WIFI,
                    ConnectionStatus.CONNECTING_WIFI -> {
                        // 假进度条
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )

                        // 循环显示的等待提示
                        Text(
                            text = waitingMessages[waitingMessageIndex],
                            color = Color.Gray,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    ConnectionStatus.NOT_STARTED -> {
                        // 连接设备按钮
                        SmartActionButton(
                            onClick = onConnectClick,
                            text = stringResource(R.string.connect_device),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    }

                    ConnectionStatus.RTSP_FAILED,
                    ConnectionStatus.WIFI_MISMATCH,
                    ConnectionStatus.WIFI_CONNECTION_FAILED,
                    ConnectionStatus.ASK_OPEN_SETTINGS -> {
                        // 错误提示
                        Text(
                            text = getErrorMessage(status),
                            color = Color.Red,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )

                        // 操作按钮
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (status == ConnectionStatus.ASK_OPEN_SETTINGS) {
                                SmartActionButton(
                                    onClick = onOpenWifiSettings,
                                    text = stringResource(R.string.open_wifi_settings),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp),
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            }

                            SmartActionButton(
                                onClick = onConnectClick,
                                text = stringResource(R.string.retry),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp),
                                containerColor = Color(0xFF4CAF50)
                            )
                        }
                    }

                    ConnectionStatus.WIFI_NOT_CONNECTED -> {
                        // Wi-Fi未连接时不显示任何内容，由对话框处理
                    }

                    ConnectionStatus.CONNECTED -> {
                        // 连接成功，自动关闭overlay
                        LaunchedEffect(Unit) {
                            delay(1000)
                            onDismiss()
                        }
                    }
                }
            }
        }

        // Wi-Fi未连接对话框
        if (showNoWifiDialog) {
            AlertDialog(
                onDismissRequest = { showNoWifiDialog = false },
                title = { Text(stringResource(R.string.wifi_not_connected)) },
                text = { Text(stringResource(R.string.no_wifi_dialog_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showNoWifiDialog = false
                            onOpenWifiSettings()
                            onResetStatus()
                        }
                    ) {
                        Text(stringResource(R.string.open_wifi_settings))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { 
                            showNoWifiDialog = false
                            onResetStatus()
                        }
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }
    }
}
