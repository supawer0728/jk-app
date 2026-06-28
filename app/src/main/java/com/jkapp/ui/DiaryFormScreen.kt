package com.jkapp.ui

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jkapp.R
import com.jkapp.data.model.Attachment
import com.jkapp.data.model.CatRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryFormScreen(
    viewModel: DiaryViewModel,
    firestoreId: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isEditMode = firestoreId != null
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val success = uiState as? DiaryUiState.Success
    val recordTypes = success?.recordTypes ?: emptyList()
    val existingRecord = if (isEditMode) success?.records?.find { it.firestoreId == firestoreId } else null

    val pendingAttachments by viewModel.pendingAttachments.collectAsStateWithLifecycle()
    val isUploadingAttachment by viewModel.isUploadingAttachment.collectAsStateWithLifecycle()

    var recordDate by rememberSaveable { mutableStateOf(existingRecord?.date ?: DiaryViewModel.todayDate()) }
    var selectedTypeId by rememberSaveable { mutableStateOf(existingRecord?.recordType ?: "") }
    var recordText by rememberSaveable { mutableStateOf(existingRecord?.record ?: "") }
    var typeDropdownExpanded by remember { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    // Existing attachments the user keeps during editing (starts empty, populated when record loads)
    var keptExistingAttachments by remember { mutableStateOf(existingRecord?.attachments ?: emptyList<Attachment>()) }

    val attachmentUploadError by viewModel.attachmentUploadError.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(attachmentUploadError) {
        attachmentUploadError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearAttachmentUploadError()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        var resolvedName = uri.lastPathSegment ?: "attachment"
        var resolvedSize = 0L
        context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) resolvedName = cursor.getString(nameIdx) ?: resolvedName
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) resolvedSize = cursor.getLong(sizeIdx)
            }
        }
        context.contentResolver.openInputStream(uri)?.let { inputStream ->
            viewModel.uploadAttachment(inputStream, resolvedName, mimeType, resolvedSize)
        }
    }

    val onCancel = {
        viewModel.cancelPendingAttachments()
        onBack()
    }

    LaunchedEffect(existingRecord) {
        existingRecord?.let { r ->
            recordDate = r.date
            selectedTypeId = r.recordType
            recordText = r.record
            keptExistingAttachments = r.attachments
        }
    }

    LaunchedEffect(recordTypes) {
        if (selectedTypeId.isEmpty() && recordTypes.isNotEmpty()) {
            selectedTypeId = recordTypes.first().id
        }
    }

    val selectedType = recordTypes.find { it.id == selectedTypeId }
    val isDataReady = !isEditMode || existingRecord != null
    val isValid = isDataReady && recordDate.isNotBlank() && selectedTypeId.isNotEmpty() && recordText.isNotBlank()

    if (showDatePicker) {
        // DatePickerState.selectedDateMillis는 UTC 자정 기준 epoch millis를 반환하므로
        // 초기값 변환과 확인 버튼 변환 모두 ZoneOffset.UTC로 통일한다.
        // DiaryViewModel.todayDate()는 시스템 시간대를 사용하므로 UTC±12h 경계 조건에서
        // 하루 차이가 날 수 있다. 이는 의도적인 tradeoff다.
        val initialMillis = runCatching {
            LocalDate.parse(recordDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrElse { System.currentTimeMillis() }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        recordDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC).toLocalDate()
                            .format(DateTimeFormatter.ISO_LOCAL_DATE)
                    }
                    showDatePicker = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (isEditMode) R.string.edit_record else R.string.add_record))
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box {
                OutlinedTextField(
                    value = recordDate,
                    onValueChange = {},
                    label = { Text(stringResource(R.string.field_date)) },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    enabled = !isEditMode,
                    singleLine = true,
                    trailingIcon = {
                        if (!isEditMode) Icon(Icons.Default.DateRange, contentDescription = null)
                    }
                )
                if (!isEditMode) {
                    Box(modifier = Modifier.matchParentSize().clickable { showDatePicker = true })
                }
            }

            ExposedDropdownMenuBox(
                expanded = typeDropdownExpanded,
                onExpandedChange = { typeDropdownExpanded = !typeDropdownExpanded }
            ) {
                val bgColor = selectedType?.backgroundColor?.toComposeColorOrNull()
                    ?: MaterialTheme.colorScheme.surface
                val fontColor = selectedType?.fontColor?.toComposeColorOrNull()
                    ?: MaterialTheme.colorScheme.onSurface

                OutlinedTextField(
                    value = selectedType?.let { "${it.emoji} ${it.name}" } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.record_type_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeDropdownExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedTextColor = fontColor,
                        unfocusedTextColor = fontColor,
                        focusedContainerColor = bgColor.copy(alpha = 0.2f),
                        unfocusedContainerColor = bgColor.copy(alpha = 0.1f)
                    )
                )
                ExposedDropdownMenu(
                    expanded = typeDropdownExpanded,
                    onDismissRequest = { typeDropdownExpanded = false }
                ) {
                    recordTypes.forEach { type ->
                        val itemBgColor = type.backgroundColor.toComposeColorOrNull()
                            ?: MaterialTheme.colorScheme.surface
                        val itemFontColor = type.fontColor.toComposeColorOrNull()
                            ?: MaterialTheme.colorScheme.onSurface

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "${type.emoji} ${type.name}",
                                    color = itemFontColor
                                )
                            },
                            onClick = {
                                selectedTypeId = type.id
                                typeDropdownExpanded = false
                            },
                            modifier = Modifier.background(itemBgColor.copy(alpha = 0.2f))
                        )
                    }
                }
            }

            OutlinedTextField(
                value = recordText,
                onValueChange = { recordText = it },
                label = { Text(stringResource(R.string.field_record)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5
            )

            // ── 첨부파일 섹션 ──────────────────────────────────────────────────────
            AttachmentSection(
                existingAttachments = keptExistingAttachments,
                pendingAttachments = pendingAttachments,
                isUploading = isUploadingAttachment,
                onRemoveExisting = { fileId ->
                    keptExistingAttachments = keptExistingAttachments.filter { it.fileId != fileId }
                },
                onRemovePending = { fileId -> viewModel.removePendingAttachment(fileId) },
                onAddFile = { filePickerLauncher.launch("*/*") },
            )

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    val record = CatRecord(
                        firestoreId = firestoreId,
                        date = recordDate,
                        recordType = selectedTypeId,
                        record = recordText.trim(),
                        attachments = keptExistingAttachments,
                    )
                    if (isEditMode && existingRecord != null) {
                        viewModel.updateRecord(existingRecord, record)
                    } else {
                        viewModel.addRecord(record)
                    }
                    onBack()
                },
                enabled = isValid && !isUploadingAttachment,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

@Composable
private fun AttachmentSection(
    existingAttachments: List<Attachment>,
    pendingAttachments: List<Attachment>,
    isUploading: Boolean,
    onRemoveExisting: (String) -> Unit,
    onRemovePending: (String) -> Unit,
    onAddFile: () -> Unit,
) {
    val totalCount = existingAttachments.size + pendingAttachments.size
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "첨부파일${if (totalCount > 0) " ($totalCount)" else ""}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (totalCount > 0) {
            HorizontalDivider()
            existingAttachments.forEach { attachment ->
                AttachmentRow(
                    attachment = attachment,
                    onRemove = { onRemoveExisting(attachment.fileId) },
                )
            }
            pendingAttachments.forEach { attachment ->
                AttachmentRow(
                    attachment = attachment,
                    onRemove = { onRemovePending(attachment.fileId) },
                )
            }
            HorizontalDivider()
        }
        if (isUploading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("업로드 중...", style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedButton(
            onClick = onAddFile,
            enabled = !isUploading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("파일 추가")
        }
    }
}

@Composable
private fun AttachmentRow(
    attachment: Attachment,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = when {
            attachment.mimeType.startsWith("image/") -> Icons.Default.Image
            else -> Icons.Default.AttachFile
        }
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = attachment.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatFileSize(attachment.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, contentDescription = "첨부파일 삭제")
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024L -> "${bytes}B"
    bytes < 1024L * 1024 -> "${bytes / 1024}KB"
    else -> "${"%.1f".format(bytes / (1024.0 * 1024))}MB"
}
