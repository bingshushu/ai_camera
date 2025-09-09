package com.ai.bb.camera

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
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
        
        // Use modern AppCompatDelegate API for all Android versions
        val localeList = when (language) {
            AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            else -> LocaleListCompat.forLanguageTags(language.code)
        }
        
        // This works on all Android versions and automatically handles per-app preferences on Android 13+
        AppCompatDelegate.setApplicationLocales(localeList)
    }
    
    fun getCurrentLanguage(): AppLanguage {
        return AppLanguage.values().find { it.code == prefs.getString("language", "system") } ?: AppLanguage.SYSTEM
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
        
        drawCircleCenterStyle(style, center, color, scale, isPreview = true)
    }
}

fun DrawScope.drawCircleCenterStyle(
    style: CircleCenterStyle,
    center: Offset,
    color: Color,
    scale: Float = 1f,
    circleRadius: Float? = null,
    isPreview: Boolean = false
) {
    val centerLineWidth = if (isPreview) 1.0f else maxOf(1.0f, (circleRadius ?: 50f) * 0.025f * scale)
    val dashPattern = floatArrayOf(1.0f, 5.0f) // 3pt实线，2pt空白
    when (style) {
        CircleCenterStyle.DOT -> {
            // iOS对齐：半径的4%，最小2pt
            val dotRadius = if (isPreview) 2.0f else maxOf(2.0f, (circleRadius ?: 50f) * 0.04f * scale)
            drawCircle(
                color = color,
                radius = dotRadius,
                center = center
            )
        }
        
        CircleCenterStyle.SMALL_CROSS -> {
            // iOS对齐：半径的40%，最小6pt
            val crossSize = if (isPreview) 6.0f else maxOf(6.0f, (circleRadius ?: 50f) * 0.4f * scale)
            // 设置虚线效果
            val pathEffect = PathEffect.dashPathEffect(dashPattern, 0f)
            
            // 水平线
            drawLine(
                color = color,
                start = Offset(center.x - crossSize, center.y),
                end = Offset(center.x + crossSize, center.y),
                strokeWidth = centerLineWidth,
                pathEffect = pathEffect
            )
            // 垂直线
            drawLine(
                color = color,
                start = Offset(center.x, center.y - crossSize),
                end = Offset(center.x, center.y + crossSize),
                strokeWidth = centerLineWidth,
                pathEffect = pathEffect
            )
        }
        
        CircleCenterStyle.LARGE_CROSS -> {
            // iOS对齐：十字延伸到圆的边缘（radius的100%）
            val crossSize = circleRadius ?: (if (isPreview) 20f else 20f * scale)
            // 设置虚线效果
            val pathEffect = PathEffect.dashPathEffect(dashPattern, 0f)
            
            // 水平线 - 延伸到圆的边缘
            drawLine(
                color = color,
                start = Offset(center.x - crossSize, center.y),
                end = Offset(center.x + crossSize, center.y),
                strokeWidth = centerLineWidth,
                pathEffect = pathEffect
            )
            // 垂直线 - 延伸到圆的边缘
            drawLine(
                color = color,
                start = Offset(center.x, center.y - crossSize),
                end = Offset(center.x, center.y + crossSize),
                strokeWidth = centerLineWidth,
                pathEffect = pathEffect
            )
        }
        
        CircleCenterStyle.SMALL_CIRCLE -> {
            // iOS对齐：半径的12%，最小4pt
            val innerRadius = if (isPreview) 4.0f else maxOf(4.0f, (circleRadius ?: 50f) * 0.12f * scale)
            // iOS对齐：小圆线宽为小圆半径的20%，最小1.0pt
            val smallCircleLineWidth = if (isPreview) 1.0f else maxOf(1.0f, innerRadius * 0.2f)
            // 设置虚线效果
            val pathEffect = PathEffect.dashPathEffect(dashPattern, 0f)
            
            drawCircle(
                color = color,
                radius = innerRadius,
                center = center,
                style = Stroke(width = smallCircleLineWidth, pathEffect = pathEffect)
            )
        }
        
        CircleCenterStyle.CROSS_WITH_CIRCLE -> {
            // iOS对齐：内圆半径的15%，最小6pt
            val innerRadius = if (isPreview) 6.0f else maxOf(6.0f, (circleRadius ?: 50f) * 0.15f * scale)
            val crossSize = circleRadius ?: (if (isPreview) 20f else 20f * scale)
            
            // 设置虚线效果

            val pathEffect = PathEffect.dashPathEffect(dashPattern, 0f)
            
            // 绘制中断的十字线（不穿过小圆）
            // 水平线 - 左侧
            drawLine(
                color = color,
                start = Offset(center.x - crossSize, center.y),
                end = Offset(center.x - innerRadius, center.y),
                strokeWidth = centerLineWidth,
                pathEffect = pathEffect
            )
            // 水平线 - 右侧  
            drawLine(
                color = color,
                start = Offset(center.x + innerRadius, center.y),
                end = Offset(center.x + crossSize, center.y),
                strokeWidth = centerLineWidth,
                pathEffect = pathEffect
            )
            // 垂直线 - 上侧
            drawLine(
                color = color,
                start = Offset(center.x, center.y - crossSize),
                end = Offset(center.x, center.y - innerRadius),
                strokeWidth = centerLineWidth,
                pathEffect = pathEffect
            )
            // 垂直线 - 下侧
            drawLine(
                color = color,
                start = Offset(center.x, center.y + innerRadius),
                end = Offset(center.x, center.y + crossSize),
                strokeWidth = centerLineWidth,
                pathEffect = pathEffect
            )
            
            // 绘制小圆 - iOS对齐：小圆线宽为内圆半径的30%，最小1.0pt
            val innerCircleLineWidth = if (isPreview) 1.0f else maxOf(1.0f, innerRadius * 0.3f)
            drawCircle(
                color = color,
                radius = innerRadius,
                center = center,
                style = Stroke(width = innerCircleLineWidth, pathEffect = pathEffect)
            )
        }
    }
}