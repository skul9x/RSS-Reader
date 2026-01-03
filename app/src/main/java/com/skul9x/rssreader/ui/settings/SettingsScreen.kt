package com.skul9x.rssreader.ui.settings

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skul9x.rssreader.data.local.ApiKeyManager
import com.skul9x.rssreader.data.local.AppPreferences
import com.skul9x.rssreader.data.local.ScreenMode
import com.skul9x.rssreader.ui.main.MainViewModel

/**
 * Main Settings screen with navigation to RSS Feeds, API Keys management, and Screen Mode settings.
 */
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToFeeds: () -> Unit,
    onOrientationSettingChanged: () -> Unit = {},
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val apiKeyManager = remember { ApiKeyManager.getInstance(context) }
    val appPreferences = remember { AppPreferences.getInstance(context) }
    
    val uiState by viewModel.uiState.collectAsState()
    
    // Track if we triggered a refresh from this screen to show specific Toasts
    var isRefreshingFromSettings by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isLoading) {
        if (isRefreshingFromSettings && !uiState.isLoading) {
            isRefreshingFromSettings = false
            if (uiState.error == null) {
                Toast.makeText(context, "Đã cập nhật dữ liệu tin tức mới nhất!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Có lỗi xảy ra: ${uiState.error}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    // State for API keys
    var apiKeys by remember { mutableStateOf(apiKeyManager.getApiKeys()) }
    var isApiKeysExpanded by remember { mutableStateOf(false) }
    var showAddKeyDialog by remember { mutableStateOf(false) }
    
    // State for screen mode
    var currentScreenMode by remember { mutableStateOf(appPreferences.getScreenMode()) }
    var isScreenModeExpanded by remember { mutableStateOf(false) }
    
    // Refresh keys function
    fun refreshKeys() {
        apiKeys = apiKeyManager.getApiKeys()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp)
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Quay lại",
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "⚙️ Cài Đặt",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Settings items - scrollable
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // RSS Feeds section
            SettingsItem(
                icon = Icons.Default.RssFeed,
                title = "Nguồn RSS",
                subtitle = "Thêm, sửa, xóa nguồn tin tức",
                onClick = onNavigateToFeeds
            )
            
            // Force Refresh section
            SettingsItem(
                icon = Icons.Default.Refresh,
                title = "Cập nhật dữ liệu",
                subtitle = if (uiState.isLoading && isRefreshingFromSettings) "Đang tải tin tức mới..." else "Tải lại tin tức từ các nguồn RSS ngay lập tức",
                onClick = {
                    if (!uiState.isLoading) {
                        isRefreshingFromSettings = true
                        viewModel.refreshNews(force = true)
                        Toast.makeText(context, "Đang cập nhật...", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            // Screen Mode section (expandable)
            ScreenModeSection(
                currentMode = currentScreenMode,
                isExpanded = isScreenModeExpanded,
                onToggleExpand = { isScreenModeExpanded = !isScreenModeExpanded },
                onModeSelected = { mode ->
                    currentScreenMode = mode
                    appPreferences.setScreenMode(mode)
                    onOrientationSettingChanged()
                    Toast.makeText(context, "Đã đổi chế độ màn hình", Toast.LENGTH_SHORT).show()
                }
            )

            // API Keys section (expandable)
            ApiKeysSection(
                apiKeys = apiKeys,
                isExpanded = isApiKeysExpanded,
                onToggleExpand = { isApiKeysExpanded = !isApiKeysExpanded },
                onAddKey = { showAddKeyDialog = true },
                onDeleteKey = { index ->
                    apiKeyManager.removeApiKey(index)
                    refreshKeys()
                    Toast.makeText(context, "Đã xóa API key", Toast.LENGTH_SHORT).show()
                },
                apiKeyManager = apiKeyManager
            )
        }
    }
    
    // Add API Key Dialog
    if (showAddKeyDialog) {
        AddApiKeyDialog(
            onDismiss = { showAddKeyDialog = false },
            onAddKey = { input ->
                // Split by whitespace (newlines, spaces, tabs) and filter valid keys
                val keys = input.split(Regex("\\s+")).filter { it.isNotBlank() }
                var addedCount = 0
                
                keys.forEach { key ->
                    // Basic validation: starts with AIza and length > 20
                    if (key.startsWith("AIza") && key.length > 20) {
                        if (apiKeyManager.addApiKey(key)) {
                            addedCount++
                        }
                    }
                }
                
                if (addedCount > 0) {
                    refreshKeys()
                    Toast.makeText(context, "Đã thêm $addedCount API key", Toast.LENGTH_SHORT).show()
                    showAddKeyDialog = false
                } else {
                    Toast.makeText(context, "Không tìm thấy API key hợp lệ hoặc key đã tồn tại", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
private fun ScreenModeSection(
    currentMode: ScreenMode,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onModeSelected: (ScreenMode) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column {
            // Header (clickable to expand/collapse)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            MaterialTheme.colorScheme.tertiaryContainer,
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ScreenRotation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Text
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Giao diện màn hình",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (currentMode) {
                            ScreenMode.LANDSCAPE -> "Ngang (Chế độ ô tô)"
                            ScreenMode.PORTRAIT -> "Dọc (Chế độ điện thoại)"
                            ScreenMode.AUTO -> "Tự động xoay"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                // Expand/Collapse icon
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }

            // Expanded content with RadioButtons
            if (isExpanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Mode options
                    val modes = listOf(
                        ScreenMode.LANDSCAPE to "Ngang (Chế độ ô tô)" to "🚗",
                        ScreenMode.PORTRAIT to "Dọc (Chế độ điện thoại)" to "📱",
                        ScreenMode.AUTO to "Tự động xoay" to "🔄"
                    )

                    modes.forEach { (modePair, emoji) ->
                        val (mode, label) = modePair
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onModeSelected(mode) }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (currentMode == mode),
                                onClick = null // Handled by Row click
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "$emoji $label",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}



@Composable
private fun ApiKeysSection(
    apiKeys: List<String>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onAddKey: () -> Unit,
    onDeleteKey: (Int) -> Unit,
    apiKeyManager: ApiKeyManager
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column {
            // Header (clickable to expand/collapse)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Text
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "API Keys Gemini",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${apiKeys.size} key đã cấu hình",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (apiKeys.isEmpty()) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        }
                    )
                }

                // Expand/Collapse icon
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }

            // Expanded content
            if (isExpanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // List of API keys
                    apiKeys.forEachIndexed { index, key ->
                        ApiKeyItem(
                            maskedKey = apiKeyManager.maskApiKey(key),
                            onDelete = { onDeleteKey(index) }
                        )
                    }
                    
                    // Empty state
                    if (apiKeys.isEmpty()) {
                        Text(
                            text = "Chưa có API key. Thêm key để sử dụng tính năng tóm tắt AI.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Add button
                    Button(
                        onClick = onAddKey,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Thêm API Key",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApiKeyItem(
    maskedKey: String,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.VpnKey,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = maskedKey,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Xóa",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun AddApiKeyDialog(
    onDismiss: () -> Unit,
    onAddKey: (String) -> Unit
) {
    val context = LocalContext.current
    var apiKeyText by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Thêm API Key",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Nhập hoặc dán API key Gemini của bạn:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                
                OutlinedTextField(
                    value = apiKeyText,
                    onValueChange = { apiKeyText = it },
                    placeholder = { Text("Dán danh sách key vào đây...\nAIza...\nAIza...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    singleLine = false,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        // Paste button
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clipData = clipboard.primaryClip
                                if (clipData != null && clipData.itemCount > 0) {
                                    val pastedText = clipData.getItemAt(0).text?.toString() ?: ""
                                    apiKeyText = pastedText.trim()
                                    Toast.makeText(context, "Đã dán từ clipboard", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Dán",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAddKey(apiKeyText) },
                enabled = apiKeyText.contains("AIza"),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Thêm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            // Arrow
            Icon(
                imageVector = Icons.AutoMirrored.Filled.List,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
