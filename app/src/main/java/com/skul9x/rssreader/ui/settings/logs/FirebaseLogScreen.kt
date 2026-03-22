package com.skul9x.rssreader.ui.settings.logs

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skul9x.rssreader.RssApplication
import com.skul9x.rssreader.data.local.entity.FirebaseLog
import com.skul9x.rssreader.data.repository.FirebaseLogRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirebaseLogScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    // Access singleton directly from Companion object
    val repository = RssApplication.getFirebaseLogRepository() ?: return 
    
    val viewModel: FirebaseLogViewModel = viewModel(
        factory = FirebaseLogViewModel.Factory(repository)
    )
    
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Firebase Logs") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val allLogs = uiState.logs.joinToString("\n") { log ->
                            "[${formatTime(log.timestamp)}] ${log.type}: ${log.message} ${log.details ?: ""}"
                        }
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, allLogs)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Logs"))
                    }) {
                        Icon(Icons.Default.Share, "Share All")
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, "Clear All")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.currentFilter == null,
                    onClick = { viewModel.setFilter(null) },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = uiState.currentFilter == FirebaseLogRepository.TYPE_SUCCESS,
                    onClick = { viewModel.setFilter(FirebaseLogRepository.TYPE_SUCCESS) },
                    label = { Text("Success") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFE8F5E9),
                        selectedLabelColor = Color(0xFF2E7D32)
                    )
                )
                FilterChip(
                    selected = uiState.currentFilter == FirebaseLogRepository.TYPE_ERROR,
                    onClick = { viewModel.setFilter(FirebaseLogRepository.TYPE_ERROR) },
                    label = { Text("Error") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFFEBEE),
                        selectedLabelColor = Color(0xFFC62828)
                    )
                )
            }

            // Log List
            if (uiState.logs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No logs found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(uiState.logs, key = { it.id }) { log ->
                        LogItem(
                            log = log,
                            onCopy = {
                                val text = "[${formatTime(log.timestamp)}] ${log.type}: ${log.message}\n${log.details ?: ""}"
                                clipboardManager.setText(AnnotatedString(text))
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
    
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Clear All Logs?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllLogs()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun LogItem(log: FirebaseLog, onCopy: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    
    val typeColor = when (log.type) {
        FirebaseLogRepository.TYPE_SUCCESS -> Color(0xFF4CAF50) // Green
        FirebaseLogRepository.TYPE_ERROR -> Color(0xFFF44336)   // Red
        else -> Color(0xFF2196F3) // Blue
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(typeColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatTime(log.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onCopy, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.ContentCopy, "Copy", modifier = Modifier.size(16.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = log.message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            
            if (log.details != null && (expanded || log.type == FirebaseLogRepository.TYPE_ERROR)) {
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = log.details,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}
