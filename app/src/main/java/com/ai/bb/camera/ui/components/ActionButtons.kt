package com.ai.bb.camera.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 自定义动作按钮样式，优化长文本处理
 */
@Composable
fun ActionButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = Color.White,
    minWidth: Int = 72,
    maxWidth: Int = 120
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.widthIn(min = minWidth.dp, max = maxWidth.dp),
        containerColor = containerColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = text,
                color = contentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )
        }
    }
}

/**
 * 垂直文字按钮，适用于长文本，使用多行显示
 */
@Composable
fun MultiLineActionButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = Color.White
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier
            .widthIn(min = 80.dp, max = 100.dp)
            .height(64.dp),
        containerColor = containerColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(modifier = Modifier.padding(8.dp)) {
            Text(
                text = text,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                lineHeight = 13.sp
            )
        }
    }
}

/**
 * 自适应按钮 - 根据文本长度自动选择最佳样式
 */
@Composable
fun SmartActionButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = Color.White
) {
    when {
        text.length <= 6 -> {
            // 短文本：标准按钮
            ActionButton(
                onClick = onClick,
                text = text,
                modifier = modifier,
                containerColor = containerColor,
                contentColor = contentColor,
                minWidth = 64,
                maxWidth = 100
            )
        }
        text.length <= 12 -> {
            // 中等长度：多行按钮
            MultiLineActionButton(
                onClick = onClick,
                text = text,
                modifier = modifier,
                containerColor = containerColor,
                contentColor = contentColor
            )
        }
        else -> {
            // 长文本：更宽的多行按钮
            FloatingActionButton(
                onClick = onClick,
                modifier = modifier
                    .widthIn(min = 96.dp, max = 128.dp)
                    .height(56.dp),
                containerColor = containerColor,
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(modifier = Modifier.padding(6.dp)) {
                    Text(
                        text = text,
                        color = contentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        lineHeight = 12.sp
                    )
                }
            }
        }
    }
}