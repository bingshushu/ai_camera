package com.ai.bb.camera

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    onNavigateBack: () -> Unit
) {
    val settings by settingsManager.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // AI Circle Recognition Toggle
            item {
                SettingItem(
                    title = stringResource(R.string.ai_circle_recognition),
                    description = stringResource(R.string.ai_circle_recognition_description)
                ) {
                    Switch(
                        checked = settings.aiCircleRecognitionEnabled,
                        onCheckedChange = { settingsManager.updateAiCircleRecognition(it) }
                    )
                }
            }
            
            // Circle Center Style Selection
            item {
                var showStyleDialog by remember { mutableStateOf(false) }
                
                SettingItem(
                    title = stringResource(R.string.circle_center_style),
                    description = stringResource(R.string.circle_center_style_description),
                    onClick = { showStyleDialog = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircleCenterStylePreview(
                            style = settings.circleCenterStyle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = getStyleName(settings.circleCenterStyle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                if (showStyleDialog) {
                    CircleCenterStyleDialog(
                        currentStyle = settings.circleCenterStyle,
                        onStyleSelected = { style ->
                            settingsManager.updateCircleCenterStyle(style)
                            showStyleDialog = false
                        },
                        onDismiss = { showStyleDialog = false }
                    )
                }
            }
            
            // Language Selection
            item {
                var showLanguageDialog by remember { mutableStateOf(false) }
                
                SettingItem(
                    title = stringResource(R.string.language),
                    description = stringResource(R.string.language_description),
                    onClick = { showLanguageDialog = true }
                ) {
                    Text(
                        text = getLanguageName(settings.language),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (showLanguageDialog) {
                    LanguageDialog(
                        currentLanguage = settings.language,
                        onLanguageSelected = { language ->
                            settingsManager.updateLanguage(language)
                            showLanguageDialog = false
                            
                            // 重启当前Activity以应用语言更改
                            if (context is Activity) {
                                context.recreate()
                            }
                        },
                        onDismiss = { showLanguageDialog = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingItem(
    title: String,
    description: String,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val modifier = if (onClick != null) {
        Modifier.clickable { onClick() }
    } else {
        Modifier
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            content()
        }
    }
}

@Composable
private fun CircleCenterStyleDialog(
    currentStyle: CircleCenterStyle,
    onStyleSelected: (CircleCenterStyle) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.circle_center_style)) },
        text = {
            Column(
                modifier = Modifier.selectableGroup()
            ) {
                CircleCenterStyle.values().forEach { style ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (style == currentStyle),
                                onClick = { onStyleSelected(style) },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (style == currentStyle),
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        CircleCenterStylePreview(
                            style = style,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = getStyleName(style),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        }
    )
}

@Composable
private fun LanguageDialog(
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language)) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .selectableGroup()
                    .height(300.dp) // 限制对话框高度，使其可滚动
            ) {
                items(AppLanguage.values()) { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (language == currentLanguage),
                                onClick = { onLanguageSelected(language) },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (language == currentLanguage),
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = getLanguageName(language),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        }
    )
}

@Composable
private fun getStyleName(style: CircleCenterStyle): String {
    return when (style) {
        CircleCenterStyle.DOT -> stringResource(R.string.style_dot)
        CircleCenterStyle.SMALL_CROSS -> stringResource(R.string.style_small_cross)
        CircleCenterStyle.LARGE_CROSS -> stringResource(R.string.style_large_cross)
        CircleCenterStyle.SMALL_CIRCLE -> stringResource(R.string.style_small_circle)
        CircleCenterStyle.CROSS_WITH_CIRCLE -> stringResource(R.string.style_cross_with_circle)
    }
}

@Composable
private fun getLanguageName(language: AppLanguage): String {
    return LanguageManager.getDisplayLanguage(language)
}