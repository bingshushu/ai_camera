package com.ai.bb.camera

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.bb.camera.ui.theme.AICameraTheme
import kotlin.math.pow
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.displayCutoutPadding

class LaserZeroFocusActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val settingsManager = AICameraApplication.getSettingsManager(newBase)
        val currentLanguage = settingsManager.getCurrentLanguage()
        val updatedContext = LanguageManager.updateLanguage(newBase, currentLanguage)
        super.attachBaseContext(updatedContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 强制横屏
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        // 隐藏状态栏和导航栏
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContent {
            AICameraTheme {
                LaserZeroFocusScreen(
                    onNavigateBack = { finish() }
                )
            }
        }
    }
}

@Composable
fun LaserZeroFocusScreen(onNavigateBack: () -> Unit) {
    var collimationParam by remember { mutableStateOf("100") } // F1 准直参数
    var focusParam by remember { mutableStateOf("200") } // F2 聚焦参数
    var minRedLightFocus by remember { mutableStateOf("") } // F3 最小红光焦点
    var laserZeroFocus by remember { mutableStateOf("") } // 激光零焦（计算结果）

    // 计算激光零焦
    fun calculateLaserZeroFocus() {
        try {
            val f1 = collimationParam.toDoubleOrNull() ?: return
            val f2 = focusParam.toDoubleOrNull() ?: return
            val f3 = minRedLightFocus.toDoubleOrNull() ?: return

            if (f1 == 0.0) {
                laserZeroFocus = "错误: F1不能为0"
                return
            }

            // 偏差量 = 0.016 × F1 × (F2/F1)² + (F2/F1)
            val ratio = f2 / f1
            val deviation = 0.016 * f1 * ratio.pow(2) + ratio

            // 激光零焦 = F3 + 偏差量
            val result = f3 + deviation

            laserZeroFocus = String.format("%.2f", result)
        } catch (e: Exception) {
            laserZeroFocus = "计算错误"
        }
    }

    // 当输入改变时自动计算
    LaunchedEffect(collimationParam, focusParam, minRedLightFocus) {
        if (collimationParam.isNotEmpty() && focusParam.isNotEmpty() && minRedLightFocus.isNotEmpty()) {
            calculateLaserZeroFocus()
        } else {
            laserZeroFocus = ""
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A)) // 暗黑色背景
    ) {
        // 关闭按钮 - 右上角，添加安全区域padding避开刘海
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .systemBarsPadding()
                .displayCutoutPadding()
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .displayCutoutPadding()
                .padding(24.dp)
                .padding(top = 48.dp), // 为关闭按钮留出空间
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // 第一行：提示文字
            Text(
                text = stringResource(R.string.laser_zero_focus_instruction),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            )

            // 第二行：三列布局
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally)
            ) {
                // 第一列：准直参数和聚焦参数
                Column(
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // 准直参数
                    ParameterInputField(
                        label = stringResource(R.string.collimation_param),
                        value = collimationParam,
                        onValueChange = { collimationParam = it },
                        hint = stringResource(R.string.default_value_100),
                        isDarkTheme = true
                    )

                    // 聚焦参数
                    ParameterInputField(
                        label = stringResource(R.string.focus_param),
                        value = focusParam,
                        onValueChange = { focusParam = it },
                        hint = stringResource(R.string.default_value_200),
                        isDarkTheme = true
                    )
                }

                // 第二列：最小红光焦点
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    ParameterInputField(
                        label = stringResource(R.string.min_red_light_focus),
                        value = minRedLightFocus,
                        onValueChange = { minRedLightFocus = it },
                        hint = stringResource(R.string.please_input),
                        isDarkTheme = true
                    )
                }

                // 第三列：激光零焦（计算结果）
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.laser_zero_focus_result),
                        fontSize = 16.sp,
                        color = Color.White,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                    )

                    // 结果显示框
                    OutlinedTextField(
                        value = laserZeroFocus,
                        onValueChange = { },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = Color.White,
                            disabledBorderColor = Color.Gray,
                            disabledLabelColor = Color.LightGray,
                            disabledContainerColor = Color(0xFF2A2A2A)
                        ),
                        enabled = false,
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center,
                            color = Color(0xFF4CAF50)
                        )
                    )

                    // 提示文字
                    Text(
                        text = stringResource(R.string.theoretical_value_reference),
                        fontSize = 12.sp,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun ParameterInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    isDarkTheme: Boolean = false
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            color = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.onSurface,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
        )

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            placeholder = { Text(hint, fontSize = 14.sp, color = if (isDarkTheme) Color.Gray else Color.Unspecified) },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                color = if (isDarkTheme) Color.White else Color.Unspecified
            ),
            colors = if (isDarkTheme) OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF2196F3),
                unfocusedBorderColor = Color.Gray,
                cursorColor = Color.White,
                focusedContainerColor = Color(0xFF2A2A2A),
                unfocusedContainerColor = Color(0xFF2A2A2A)
            ) else OutlinedTextFieldDefaults.colors()
        )
    }
}
