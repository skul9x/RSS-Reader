package com.skul9x.rssreader.ui.settings

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skul9x.rssreader.auth.AuthManager
import com.skul9x.rssreader.auth.AuthViewModel
import com.skul9x.rssreader.data.local.ApiKeyManager
import com.skul9x.rssreader.data.local.AppPreferences
import com.skul9x.rssreader.ui.main.MainViewModel

/**
 * Main Settings screen with navigation to RSS Feeds, API Keys management, and Screen Mode settings.
 * Component sections have been extracted to separate files for better maintainability.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToFeeds: () -> Unit,
    onOrientationSettingChanged: () -> Unit = {},
    onNavigateToActivityLogs: () -> Unit = {},
    onNavigateToFirebaseLog: () -> Unit = {},
    onNavigateToHtmlAnalyzer: () -> Unit = {},
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val apiKeyManager = remember { ApiKeyManager.getInstance(context) }
    val appPreferences = remember { AppPreferences.getInstance(context) }
    
    val uiState by viewModel.uiState.collectAsState()
    
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
    
    // State for audio settings
    var isAudioStreamExpanded by remember { mutableStateOf(false) }
    var isAudioMixExpanded by remember { mutableStateOf(false) }

    // Auth ViewModel
    val authManager = remember { AuthManager.getInstance(context) }
    val authViewModel = remember { AuthViewModel(authManager) }

    
    fun refreshKeys() {
        apiKeys = apiKeyManager.getApiKeys()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Cài đặt",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lại"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Account section (Google Sign-In for sync)
            AccountSection(authViewModel = authViewModel)
            
            // RSS Feeds management
            SettingsItem(
                icon = Icons.Default.RssFeed,
                title = "Nguồn tin RSS",
                subtitle = "Quản lý danh sách nguồn tin",
                onClick = onNavigateToFeeds
            )
            
            // Reload data button
            SettingsItem(
                icon = Icons.Default.Refresh,
                title = "Tải lại dữ liệu",
                subtitle = "Cập nhật tin tức mới nhất",
                onClick = {
                    if (!uiState.isLoading) {
                        isRefreshingFromSettings = true
                        viewModel.refreshNews()
                        Toast.makeText(context, "Đang cập nhật...", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            
            // Battery Optimization section
            BatteryOptimizationSection(context = context)

            // Debug Log section (Media Buttons)
            DebugLogSection()

            // Activity Logs section
            SettingsItem(
                icon = Icons.Default.History,
                title = "Activity Logs",
                subtitle = "Xem nhật ký hoạt động và lỗi",
                onClick = onNavigateToActivityLogs
            )

            // Firebase Logs section
            SettingsItem(
                icon = Icons.Default.CloudSync,
                title = "Firebase Log",
                subtitle = "Nhật ký đồng bộ Firebase",
                onClick = onNavigateToFirebaseLog
            )

            // Audio Stream Mode section (expandable)
            AudioStreamSection(
                currentMode = appPreferences.getAudioStreamMode(),
                isExpanded = isAudioStreamExpanded,
                onToggleExpand = { isAudioStreamExpanded = !isAudioStreamExpanded },
                onModeSelected = { mode ->
                    appPreferences.setAudioStreamMode(mode)
                    // Trigger recomposition to update UI
                    isAudioStreamExpanded = false
                    Toast.makeText(context, "Đã đổi luồng âm thanh", Toast.LENGTH_SHORT).show()
                }
            )

            // Audio Mix Mode section (expandable)
            AudioMixSection(
                currentMode = appPreferences.getAudioMixMode(),
                isExpanded = isAudioMixExpanded,
                onToggleExpand = { isAudioMixExpanded = !isAudioMixExpanded },
                onModeSelected = { mode ->
                    appPreferences.setAudioMixMode(mode)
                    // Trigger recomposition to update UI
                    isAudioMixExpanded = false // Collapse after selection
                    Toast.makeText(context, "Đã đổi chế độ trộn âm thanh", Toast.LENGTH_SHORT).show()
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
            // HTML Analyzer (Debug Tool)
            SettingsItem(
                icon = Icons.Default.BugReport,
                title = "HTML Analyzer",
                subtitle = "Debug content extraction",
                onClick = onNavigateToHtmlAnalyzer
            )
        }
    }
    
    // Add API Key Dialog
    if (showAddKeyDialog) {
        AddApiKeyDialog(
            onDismiss = { showAddKeyDialog = false },
            onAddKey = { input ->
                val keys = input.split(Regex("\\s+")).filter { it.isNotBlank() }
                var addedCount = 0
                
                keys.forEach { key ->
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

            Icon(
                imageVector = Icons.AutoMirrored.Filled.List,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
