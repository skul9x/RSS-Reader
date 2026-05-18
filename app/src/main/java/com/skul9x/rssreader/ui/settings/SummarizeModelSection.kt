package com.skul9x.rssreader.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skul9x.rssreader.data.network.gemini.DynamicModel

@Composable
fun SummarizeModelSection(
    currentModelId: String,
    models: List<DynamicModel>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onModelSelected: (String) -> Unit,
    onAddModel: (String, String) -> Unit,
    onDeleteModel: (String) -> Unit,
    onUpdateModel: (String, String, String) -> Unit,
    onResetDefaults: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var modelToEdit by remember { mutableStateOf<DynamicModel?>(null) }

    val currentDisplayName = remember(currentModelId, models) {
        models.firstOrNull { it.modelId == currentModelId }?.displayName ?: currentModelId
    }

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
                            MaterialTheme.colorScheme.tertiaryContainer,
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Model Tóm tắt (AI)",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentDisplayName,
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
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    models.forEach { model ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (currentModelId == model.modelId) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                    } else {
                                        androidx.compose.ui.graphics.Color.Transparent
                                    },
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onModelSelected(model.modelId) }
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (currentModelId == model.modelId),
                                onClick = { onModelSelected(model.modelId) }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = model.displayName,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (currentModelId == model.modelId) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = model.modelId,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }

                            // Edit Action
                            IconButton(
                                onClick = {
                                    modelToEdit = model
                                    showEditDialog = true
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Chỉnh sửa",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Delete Action
                            IconButton(
                                onClick = { onDeleteModel(model.modelId) },
                                enabled = (currentModelId != model.modelId)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Xóa",
                                    tint = if (currentModelId != model.modelId) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Add Custom Model Button
                    Button(
                        onClick = { showAddDialog = true },
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
                            text = "Thêm mô hình tùy chỉnh",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }

                    // Reset to Defaults Button
                    TextButton(
                        onClick = onResetDefaults,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Khôi phục mặc định",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Medium
                            )
                        )
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
                            text = "Lưu ý: Các model Preview có thể không ổn định nhưng thường thông minh hơn.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        val existingModelIds = models.map { it.modelId }
        AddModelDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { id, name ->
                onAddModel(id, name)
                showAddDialog = false
            },
            existingModelIds = existingModelIds
        )
    }

    if (showEditDialog) {
        val existingModelIds = models.map { it.modelId }
        modelToEdit?.let { model ->
            EditModelDialog(
                model = model,
                onDismiss = { showEditDialog = false },
                onUpdate = { oldId, newId, newName ->
                    onUpdateModel(oldId, newId, newName)
                    showEditDialog = false
                },
                existingModelIds = existingModelIds
            )
        }
    }
}

@Composable
fun AddModelDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit,
    existingModelIds: List<String>
) {
    var modelId by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }

    var modelIdError by remember { mutableStateOf<String?>(null) }
    var displayNameError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Thêm mô hình tùy chỉnh",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    OutlinedTextField(
                        value = modelId,
                        onValueChange = {
                            modelId = it
                            modelIdError = null
                        },
                        label = { Text("ID Mô hình") },
                        placeholder = { Text("models/gemini-2.5-pro") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        isError = modelIdError != null
                    )
                    if (modelIdError != null) {
                        Text(
                            text = modelIdError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                    }
                }

                Column {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = {
                            displayName = it
                            displayNameError = null
                        },
                        label = { Text("Tên hiển thị") },
                        placeholder = { Text("Gemini 2.5 Pro") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        isError = displayNameError != null
                    )
                    if (displayNameError != null) {
                        Text(
                            text = displayNameError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    var hasError = false
                    if (modelId.isBlank()) {
                        modelIdError = "ID mô hình không được để trống"
                        hasError = true
                    } else if (!modelId.startsWith("models/")) {
                        modelIdError = "ID mô hình phải bắt đầu bằng 'models/'"
                        hasError = true
                    } else if (modelId.length <= 10) {
                        modelIdError = "ID mô hình quá ngắn"
                        hasError = true
                    } else if (existingModelIds.contains(modelId)) {
                        modelIdError = "ID mô hình đã tồn tại"
                        hasError = true
                    }

                    if (displayName.isBlank()) {
                        displayNameError = "Tên hiển thị không được để trống"
                        hasError = true
                    }

                    if (!hasError) {
                        onAdd(modelId.trim(), displayName.trim())
                    }
                },
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
fun EditModelDialog(
    model: DynamicModel,
    onDismiss: () -> Unit,
    onUpdate: (String, String, String) -> Unit,
    existingModelIds: List<String>
) {
    var modelId by remember { mutableStateOf(model.modelId) }
    var displayName by remember { mutableStateOf(model.displayName) }

    var modelIdError by remember { mutableStateOf<String?>(null) }
    var displayNameError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Chỉnh sửa mô hình",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    OutlinedTextField(
                        value = modelId,
                        onValueChange = {
                            modelId = it
                            modelIdError = null
                        },
                        label = { Text("ID Mô hình") },
                        placeholder = { Text("models/gemini-2.5-pro") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        isError = modelIdError != null
                    )
                    if (modelIdError != null) {
                        Text(
                            text = modelIdError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                    }
                }

                Column {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = {
                            displayName = it
                            displayNameError = null
                        },
                        label = { Text("Tên hiển thị") },
                        placeholder = { Text("Gemini 2.5 Pro") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        isError = displayNameError != null
                    )
                    if (displayNameError != null) {
                        Text(
                            text = displayNameError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    var hasError = false
                    if (modelId.isBlank()) {
                        modelIdError = "ID mô hình không được để trống"
                        hasError = true
                    } else if (!modelId.startsWith("models/")) {
                        modelIdError = "ID mô hình phải bắt đầu bằng 'models/'"
                        hasError = true
                    } else if (modelId.length <= 10) {
                        modelIdError = "ID mô hình quá ngắn"
                        hasError = true
                    } else if (modelId != model.modelId && existingModelIds.contains(modelId)) {
                        modelIdError = "ID mô hình đã tồn tại"
                        hasError = true
                    }

                    if (displayName.isBlank()) {
                        displayNameError = "Tên hiển thị không được để trống"
                        hasError = true
                    }

                    if (!hasError) {
                        onUpdate(model.modelId, modelId.trim(), displayName.trim())
                    }
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cập nhật")
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
