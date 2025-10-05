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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    modelUpdateManager: ModelUpdateManager,
    onNavigateBack: () -> Unit
) {
    val settings by settingsManager.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 模型更新相关状态
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showProgressDialog by remember { mutableStateOf(false) }
    var updateVersionInfo by remember { mutableStateOf<ModelVersionInfo?>(null) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var updateMessage by remember { mutableStateOf("") }
    
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

            // 模型更新选项
            item {
                SettingItem(
                    title = stringResource(R.string.check_model_update),
                    description = stringResource(R.string.check_model_update_description),
                    onClick = {
                        if (!isCheckingUpdate) {
                            isCheckingUpdate = true
                            scope.launch {
                                try {
                                    val versionInfo = modelUpdateManager.checkForUpdate()
                                    if (versionInfo != null) {
                                        updateVersionInfo = versionInfo
                                        showUpdateDialog = true
                                    } else {
                                        updateMessage = context.getString(R.string.model_already_latest)
                                        showUpdateDialog = true
                                    }
                                } catch (e: Exception) {
                                    updateMessage = context.getString(R.string.check_update_failed)
                                    showUpdateDialog = true
                                } finally {
                                    isCheckingUpdate = false
                                }
                            }
                        }
                    }
                ) {
                    if (isCheckingUpdate) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.current_version, modelUpdateManager.getCurrentModelVersion()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // 模型更新确认对话框
    if (showUpdateDialog && updateVersionInfo != null) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text(stringResource(R.string.model_update_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.model_update_available_message,
                        updateVersionInfo!!.version
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUpdateDialog = false
                        showProgressDialog = true
                        downloadProgress = 0

                        scope.launch {
                            val success = modelUpdateManager.downloadModel(updateVersionInfo!!) { progress ->
                                downloadProgress = progress
                            }

                            showProgressDialog = false
                            updateMessage = if (success) {
                                context.getString(R.string.model_update_success)
                            } else {
                                context.getString(R.string.model_update_failed)
                            }
                            updateVersionInfo = null
                            showUpdateDialog = true
                        }
                    }
                ) {
                    Text(stringResource(R.string.model_update_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text(stringResource(R.string.model_update_cancel))
                }
            }
        )
    }

    // 无更新或错误提示对话框
    if (showUpdateDialog && updateVersionInfo == null && updateMessage.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = {
                showUpdateDialog = false
                updateMessage = ""
            },
            title = { Text(stringResource(R.string.model_update_title)) },
            text = { Text(updateMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUpdateDialog = false
                        updateMessage = ""
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }

    // 下载进度对话框
    if (showProgressDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.model_download_title)) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.model_downloading_progress, downloadProgress))
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = { }
        )
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
            LazyColumn(
                modifier = Modifier
                    .selectableGroup()
                    .height(250.dp) // 限制对话框高度，使其可滚动
            ) {
                items(CircleCenterStyle.values()) { style ->
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