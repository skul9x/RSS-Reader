package com.skul9x.rssreader.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skul9x.rssreader.data.local.AudioStreamMode

@Composable
fun AudioStreamSection(
    currentMode: AudioStreamMode,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onModeSelected: (AudioStreamMode) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            MaterialTheme.colorScheme.secondaryContainer,
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Luồng âm thanh",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (currentMode) {
                            AudioStreamMode.MEDIA -> "Hệ thống (Nhạc/Media)"
                            AudioStreamMode.ALARM -> "Báo thức"
                            AudioStreamMode.NOTIFICATION -> "Thông báo"
                            AudioStreamMode.RINGTONE -> "Nhạc chuông"
                            AudioStreamMode.NAVIGATION -> "Dẫn đường"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }

            if (isExpanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val modes = listOf(
                        AudioStreamMode.MEDIA to "Hệ thống (Nhạc/Media)" to "🎵",
                        AudioStreamMode.ALARM to "Báo thức" to "⏰",
                        AudioStreamMode.NOTIFICATION to "Thông báo" to "🔔",
                        AudioStreamMode.RINGTONE to "Nhạc chuông" to "📞",
                        AudioStreamMode.NAVIGATION to "Dẫn đường" to "🧭"
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
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "$emoji $label",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Lưu ý: Thay đổi luồng âm thanh sẽ có hiệu lực ở lần đọc tin tiếp theo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
            }
        }
    }
}
