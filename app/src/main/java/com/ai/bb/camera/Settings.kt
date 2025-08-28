package com.ai.bb.camera

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class CircleCenterStyle {
    DOT,
    SMALL_CROSS,
    LARGE_CROSS,
    SMALL_CIRCLE,
    CROSS_WITH_CIRCLE
}

enum class AppLanguage(val code: String, val displayName: String) {
    SYSTEM("system", "跟随系统"),
    CHINESE("zh", "简体中文"),
    ENGLISH("en", "English"),
    SPANISH("es", "Español"),
    FRENCH("fr", "Français"),
    ARABIC("ar", "العربية"),
    RUSSIAN("ru", "Русский"),
    GERMAN("de", "Deutsch"),
    PORTUGUESE("pt", "Português"),
    ITALIAN("it", "Italiano")
}

data class AppSettings(
    val aiCircleRecognitionEnabled: Boolean = true,
    val circleCenterStyle: CircleCenterStyle = CircleCenterStyle.CROSS_WITH_CIRCLE,
    val language: AppLanguage = AppLanguage.SYSTEM
)

class SettingsManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    
    private fun loadSettings(): AppSettings {
        return AppSettings(
            aiCircleRecognitionEnabled = prefs.getBoolean("ai_circle_recognition", true),
            circleCenterStyle = CircleCenterStyle.valueOf(
                prefs.getString("circle_center_style", CircleCenterStyle.CROSS_WITH_CIRCLE.name) ?: CircleCenterStyle.CROSS_WITH_CIRCLE.name
            ),
            language = AppLanguage.values().find { it.code == prefs.getString("language", "system") } ?: AppLanguage.SYSTEM
        )
    }
    
    fun updateAiCircleRecognition(enabled: Boolean) {
        prefs.edit().putBoolean("ai_circle_recognition", enabled).apply()
        _settings.value = _settings.value.copy(aiCircleRecognitionEnabled = enabled)
    }
    
    fun updateCircleCenterStyle(style: CircleCenterStyle) {
        prefs.edit().putString("circle_center_style", style.name).apply()
        _settings.value = _settings.value.copy(circleCenterStyle = style)
    }
    
    fun updateLanguage(language: AppLanguage) {
        prefs.edit().putString("language", language.code).apply()
        _settings.value = _settings.value.copy(language = language)
    }
}

@Composable
fun CircleCenterStylePreview(
    style: CircleCenterStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Canvas(modifier = modifier.size(40.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val scale = 1f
        
        drawCircleCenterStyle(style, center, color, scale)
    }
}

fun DrawScope.drawCircleCenterStyle(
    style: CircleCenterStyle,
    center: Offset,
    color: Color,
    scale: Float = 1f
) {
    val strokeWidth = 2f / scale
    val baseSize = 8f / scale
    
    when (style) {
        CircleCenterStyle.DOT -> {
            drawCircle(
                color = color,
                radius = 3f / scale,
                center = center
            )
        }
        
        CircleCenterStyle.SMALL_CROSS -> {
            val crossSize = baseSize
            // 水平线
            drawLine(
                color = color,
                start = Offset(center.x - crossSize, center.y),
                end = Offset(center.x + crossSize, center.y),
                strokeWidth = strokeWidth
            )
            // 垂直线
            drawLine(
                color = color,
                start = Offset(center.x, center.y - crossSize),
                end = Offset(center.x, center.y + crossSize),
                strokeWidth = strokeWidth
            )
        }
        
        CircleCenterStyle.LARGE_CROSS -> {
            val crossSize = baseSize * 2f
            // 水平线
            drawLine(
                color = color,
                start = Offset(center.x - crossSize, center.y),
                end = Offset(center.x + crossSize, center.y),
                strokeWidth = strokeWidth
            )
            // 垂直线
            drawLine(
                color = color,
                start = Offset(center.x, center.y - crossSize),
                end = Offset(center.x, center.y + crossSize),
                strokeWidth = strokeWidth
            )
        }
        
        CircleCenterStyle.SMALL_CIRCLE -> {
            drawCircle(
                color = color,
                radius = baseSize,
                center = center,
                style = Stroke(width = strokeWidth)
            )
        }
        
        CircleCenterStyle.CROSS_WITH_CIRCLE -> {
            // 外圈 - 粗线条
            drawCircle(
                color = color,
                radius = baseSize * 1.5f,
                center = center,
                style = Stroke(width = strokeWidth * 2f)
            )
            
            // 内部十字 - 细线条
            val crossSize = baseSize * 0.8f
            val innerStrokeWidth = strokeWidth * 0.8f
            // 水平线
            drawLine(
                color = color,
                start = Offset(center.x - crossSize, center.y),
                end = Offset(center.x + crossSize, center.y),
                strokeWidth = innerStrokeWidth
            )
            // 垂直线
            drawLine(
                color = color,
                start = Offset(center.x, center.y - crossSize),
                end = Offset(center.x, center.y + crossSize),
                strokeWidth = innerStrokeWidth
            )
            
            // 中心点
            drawCircle(
                color = color,
                radius = 1.5f / scale,
                center = center
            )
        }
    }
}