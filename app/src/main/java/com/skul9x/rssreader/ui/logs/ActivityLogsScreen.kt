package com.skul9x.rssreader.ui.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skul9x.rssreader.data.model.ActivityLog
import com.skul9x.rssreader.utils.ActivityLogger
import java.text.SimpleDateFormat
import java.util.*

/**
 * Screen to display activity logs for debugging shared link processing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLogsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ActivityLogsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    // Build all logs text for copy/share
    val allLogsText = remember(uiState.logs) {
        buildAllLogsText(uiState.logs)
    }
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Activity Logs",
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
                actions = {
                    // Copy all logs
                    IconButton(
                        onClick = {
                            if (uiState.logs.isNotEmpty()) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Activity Logs", allLogsText))
                                Toast.makeText(context, "Đã sao chép ${uiState.logs.size} logs", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = uiState.logs.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy tất cả logs"
                        )
                    }
                    // Share all logs
                    IconButton(
                        onClick = {
                            if (uiState.logs.isNotEmpty()) {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, allLogsText)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Chia sẻ logs"))
                            }
                        },
                        enabled = uiState.logs.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share tất cả logs"
                        )
                    }
                    // Delete all logs
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Xóa tất cả logs"
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
        ) {
            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !uiState.showErrorsOnly,
                    onClick = { if (uiState.showErrorsOnly) viewModel.toggleFilter() },
                    label = { Text("Tất cả") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
                FilterChip(
                    selected = uiState.showErrorsOnly,
                    onClick = { if (!uiState.showErrorsOnly) viewModel.toggleFilter() },
                    label = { Text("Chỉ lỗi") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                Text(
                    text = "${uiState.logs.size} logs",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.CenterVertically),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            HorizontalDivider()
            
            // Logs list
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.logs.isEmpty()) {
                EmptyLogsView(showErrorsOnly = uiState.showErrorsOnly)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.logs, key = { it.id }) { log ->
                        LogItem(
                            log = log,
                            onDelete = { viewModel.deleteLog(log.id) }
                        )
                    }
                }
            }
        }
    }
    
    // Clear confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Xóa tất cả logs?") },
            text = { Text("Hành động này không thể hoàn tác.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllLogs()
                        showClearDialog = false
                    }
                ) {
                    Text("Xóa")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }
}

/**
 * Individual log item card.
 */
@Composable
private fun LogItem(
    log: ActivityLog,
    onDelete: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    val (icon, iconColor) = getEventIcon(log.eventType, log.isError)
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (log.isError) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Event icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(iconColor.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                // Event type and time
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = log.message,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${dateFormat.format(Date(log.timestamp))} • ${timeFormat.format(Date(log.timestamp))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Expand icon
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Thu gọn" else "Mở rộng",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // URL
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Text(
                    text = log.url,
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 1
                )
            }
            
            // Expandable details
            AnimatedVisibility(visible = isExpanded && log.details != null) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Chi tiết",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = log.details ?: "",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            
            // Delete button (always visible when expanded)
            AnimatedVisibility(visible = isExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDelete,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Xóa log này", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/**
 * Empty state view.
 */
@Composable
private fun EmptyLogsView(showErrorsOnly: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (showErrorsOnly) Icons.Default.CheckCircle else Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = if (showErrorsOnly) "Không có lỗi" else "Chưa có activity log",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (showErrorsOnly) "Hệ thống đang hoạt động tốt!" else "Chia sẻ một link để bắt đầu",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Get icon and color based on event type.
 */
private fun getEventIcon(eventType: String, isError: Boolean): Pair<androidx.compose.ui.graphics.vector.ImageVector, Color> {
    return when {
        isError -> Icons.Default.Error to Color(0xFFEF5350)
        eventType == ActivityLogger.EVENT_LINK_RECEIVED -> Icons.Default.Link to Color(0xFF42A5F5)
        eventType == ActivityLogger.EVENT_FETCH_START -> Icons.Default.Download to Color(0xFF66BB6A)
        eventType == ActivityLogger.EVENT_FETCH_SUCCESS -> Icons.Default.CheckCircle to Color(0xFF66BB6A)
        eventType == ActivityLogger.EVENT_HTTP_REQUEST -> Icons.AutoMirrored.Filled.Send to Color(0xFF9575CD)
        eventType == ActivityLogger.EVENT_HTTP_SUCCESS -> Icons.Default.Done to Color(0xFF66BB6A)
        eventType == ActivityLogger.EVENT_JSOUP_PARSE -> Icons.Default.Code to Color(0xFFFFCA28)
        eventType == ActivityLogger.EVENT_GEMINI_START -> Icons.Default.Psychology to Color(0xFF26C6DA)
        eventType == ActivityLogger.EVENT_GEMINI_SUCCESS -> Icons.Default.AutoAwesome to Color(0xFF26C6DA)
        else -> Icons.Default.Circle to Color.Gray
    }
}

/**
 * Build formatted text for all logs (for copy/share).
 */
private fun buildAllLogsText(logs: List<ActivityLog>): String {
    if (logs.isEmpty()) return ""
    
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    
    return buildString {
        appendLine("=== Activity Logs (${logs.size} entries) ===")
        appendLine()
        
        logs.forEach { log ->
            appendLine("[${if (log.isError) "ERROR" else "INFO"}] ${log.message}")
            appendLine("Time: ${dateFormat.format(Date(log.timestamp))}")
            appendLine("URL: ${log.url}")
            if (log.details != null) {
                appendLine("Details: ${log.details}")
            }
            appendLine("---")
        }
    }
}
