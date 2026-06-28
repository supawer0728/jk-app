package com.jkapp.ui

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.jkapp.R
import com.jkapp.data.model.Attachment
import com.jkapp.data.model.CatRecord
import com.jkapp.data.model.CatRecordType
import kotlinx.coroutines.launch
import java.io.File

private const val MAX_VISIBLE_THUMBNAILS = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryDetailScreen(
    viewModel: DiaryViewModel,
    date: String,
    onBack: () -> Unit,
    onNavigateToEdit: (firestoreId: String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val success = uiState as? DiaryUiState.Success
    val records = success?.records?.filter { it.date == date } ?: emptyList()
    val recordTypes = success?.recordTypes ?: emptyList()
    val downloadingIds by viewModel.downloadingAttachmentIds.collectAsStateWithLifecycle()

    LaunchedEffect(success, records) {
        if (success != null && records.isEmpty()) onBack()
    }

    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${date} (${DiaryViewModel.computeDayOfWeek(date)})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (records.isEmpty()) {
            Text(
                text = stringResource(R.string.record_not_found),
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                records.forEachIndexed { index, record ->
                    if (index > 0) HorizontalDivider()
                    RecordDetailItem(
                        record = record,
                        type = recordTypes.find { it.id.trim().equals(record.recordType.trim(), ignoreCase = true) },
                        downloadingIds = downloadingIds,
                        onEdit = { record.firestoreId?.let(onNavigateToEdit) },
                        onDeleteRequest = { pendingDeleteId = record.firestoreId },
                        onDownloadAttachment = { attachment, destFile ->
                            viewModel.downloadAttachment(attachment.fileId, destFile)
                        },
                        onOpenNonImageAttachment = { attachment, cacheDir ->
                            viewModel.downloadAttachment(attachment.fileId, attachmentCacheFile(cacheDir, attachment))
                        },
                    )
                }
            }
        }
    }

    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_message, date)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRecord(id)
                    pendingDeleteId = null
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun RecordDetailItem(
    record: CatRecord,
    type: CatRecordType?,
    downloadingIds: Set<String>,
    onEdit: () -> Unit,
    onDeleteRequest: () -> Unit,
    onDownloadAttachment: suspend (Attachment, File) -> Unit,
    onOpenNonImageAttachment: suspend (Attachment, File) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bgColor = type?.backgroundColor?.toComposeColorOrNull()
        ?: MaterialTheme.colorScheme.secondaryContainer
    val fontColor = type?.fontColor?.toComposeColorOrNull()
        ?: MaterialTheme.colorScheme.onSecondaryContainer

    val imageAttachments = record.attachments.filter { it.mimeType.startsWith("image/") }
    val otherAttachments = record.attachments.filter { !it.mimeType.startsWith("image/") }
    Log.d("DiaryDetail", "첨부파일 분류: 전체=${record.attachments.size}, 이미지=${imageAttachments.size}, 기타=${otherAttachments.size}")
    imageAttachments.forEach { Log.d("DiaryDetail", "  이미지 첨부: id=${it.fileId}, name=${it.name}, mime=${it.mimeType}") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SuggestionChip(
                onClick = {},
                label = {
                    Text(
                        text = type?.let { "${it.emoji} ${it.name}" } ?: record.recordType,
                        style = MaterialTheme.typography.labelMedium,
                        color = fontColor
                    )
                },
                colors = SuggestionChipDefaults.suggestionChipColors(containerColor = bgColor),
                border = null
            )
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                }
                IconButton(onClick = onDeleteRequest) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        Text(
            text = record.record,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        if (record.attachments.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                text = "첨부파일 (${record.attachments.size})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (imageAttachments.isNotEmpty()) {
                ImageAttachmentSection(
                    imageAttachments = imageAttachments,
                    downloadingIds = downloadingIds,
                    cacheDir = context.cacheDir,
                    onDownloadAttachment = onDownloadAttachment,
                )
            }

            otherAttachments.forEach { attachment ->
                val isDownloading = attachment.fileId in downloadingIds
                AttachmentDetailRow(
                    attachment = attachment,
                    isDownloading = isDownloading,
                    onClick = {
                        scope.launch {
                            val destFile = attachmentCacheFile(context.cacheDir, attachment)
                            onOpenNonImageAttachment(attachment, context.cacheDir)
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.provider",
                                destFile,
                            )
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, attachment.mimeType)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            if (intent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(intent)
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ImageAttachmentSection(
    imageAttachments: List<Attachment>,
    downloadingIds: Set<String>,
    cacheDir: File,
    onDownloadAttachment: suspend (Attachment, File) -> Unit,
) {
    val imageFiles = remember { mutableStateMapOf<String, File>() }
    var viewerStartIndex by remember { mutableStateOf<Int?>(null) }

    val visibleAttachments = imageAttachments.take(MAX_VISIBLE_THUMBNAILS)
    val overflowCount = imageAttachments.size - MAX_VISIBLE_THUMBNAILS

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        visibleAttachments.forEachIndexed { index, attachment ->
            val isLastVisible = index == MAX_VISIBLE_THUMBNAILS - 1 && overflowCount > 0
            ImageThumbnailItem(
                attachment = attachment,
                isOverlay = isLastVisible,
                overlayCount = overflowCount,
                isDownloading = attachment.fileId in downloadingIds,
                cacheDir = cacheDir,
                cachedFile = imageFiles[attachment.fileId],
                onFileReady = { fileId, file -> imageFiles[fileId] = file },
                onDownloadAttachment = onDownloadAttachment,
                onClick = { viewerStartIndex = index },
            )
        }
    }

    viewerStartIndex?.let { startIndex ->
        ImageViewerDialog(
            attachments = imageAttachments,
            startIndex = startIndex,
            cacheDir = cacheDir,
            imageFiles = imageFiles,
            downloadingIds = downloadingIds,
            onDownloadAttachment = onDownloadAttachment,
            onFileReady = { fileId, file -> imageFiles[fileId] = file },
            onDismiss = { viewerStartIndex = null },
        )
    }
}

@Composable
private fun ImageThumbnailItem(
    attachment: Attachment,
    isOverlay: Boolean,
    overlayCount: Int,
    isDownloading: Boolean,
    cacheDir: File,
    cachedFile: File?,
    onFileReady: (String, File) -> Unit,
    onDownloadAttachment: suspend (Attachment, File) -> Unit,
    onClick: () -> Unit,
) {
    val destFile = remember(attachment.fileId) { attachmentCacheFile(cacheDir, attachment) }

    LaunchedEffect(attachment.fileId) {
        Log.d("DiaryDetail", "썸네일 LaunchedEffect: id=${attachment.fileId}, name=${attachment.name}, destFile=${destFile.absolutePath}, exists=${destFile.exists()}")
        if (!destFile.exists()) {
            Log.d("DiaryDetail", "썸네일 다운로드 시작: id=${attachment.fileId}")
            onDownloadAttachment(attachment, destFile)
            Log.d("DiaryDetail", "썸네일 다운로드 완료: id=${attachment.fileId}, exists=${destFile.exists()}, size=${if (destFile.exists()) destFile.length() else -1}")
        }
        if (destFile.exists()) {
            Log.d("DiaryDetail", "onFileReady 호출: id=${attachment.fileId}")
            onFileReady(attachment.fileId, destFile)
        } else {
            Log.w("DiaryDetail", "다운로드 후에도 파일 없음: id=${attachment.fileId}")
        }
    }

    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val displayFile = cachedFile ?: if (destFile.exists()) destFile else null
        Log.d("DiaryDetail", "썸네일 렌더링: id=${attachment.fileId}, cachedFile=$cachedFile, displayFile=$displayFile, isDownloading=$isDownloading")
        when {
            displayFile != null -> AsyncImage(
                model = displayFile,
                contentDescription = attachment.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onLoading = { Log.d("DiaryDetail", "Coil 로딩 중: ${attachment.fileId}, path=${displayFile.absolutePath}") },
                onSuccess = { Log.d("DiaryDetail", "Coil 로드 성공: ${attachment.fileId}") },
                onError = { Log.e("DiaryDetail", "Coil 로드 실패: ${attachment.fileId}, error=${it.result.throwable}") },
            )
            isDownloading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            else -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (isOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+$overlayCount",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun ImageViewerDialog(
    attachments: List<Attachment>,
    startIndex: Int,
    cacheDir: File,
    imageFiles: Map<String, File>,
    downloadingIds: Set<String>,
    onDownloadAttachment: suspend (Attachment, File) -> Unit,
    onFileReady: (String, File) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            val pagerState = rememberPagerState(initialPage = startIndex) { attachments.size }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val attachment = attachments[page]
                ImageViewerPage(
                    attachment = attachment,
                    cacheDir = cacheDir,
                    cachedFile = imageFiles[attachment.fileId],
                    isDownloading = attachment.fileId in downloadingIds,
                    onDownloadAttachment = onDownloadAttachment,
                    onFileReady = onFileReady,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp)
                    .align(Alignment.TopStart),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = Color.White)
                }
                Text(
                    text = "${pagerState.currentPage + 1} / ${attachments.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.width(48.dp))
            }
        }
    }
}

@Composable
private fun ImageViewerPage(
    attachment: Attachment,
    cacheDir: File,
    cachedFile: File?,
    isDownloading: Boolean,
    onDownloadAttachment: suspend (Attachment, File) -> Unit,
    onFileReady: (String, File) -> Unit,
) {
    val destFile = remember(attachment.fileId) { attachmentCacheFile(cacheDir, attachment) }
    var fileReady by remember(attachment.fileId) { mutableStateOf(cachedFile != null || destFile.exists()) }

    LaunchedEffect(attachment.fileId) {
        Log.d("DiaryDetail", "뷰어 LaunchedEffect: id=${attachment.fileId}, destFile=${destFile.absolutePath}, exists=${destFile.exists()}, cachedFile=$cachedFile")
        if (!destFile.exists()) {
            Log.d("DiaryDetail", "뷰어 다운로드 시작: id=${attachment.fileId}")
            onDownloadAttachment(attachment, destFile)
            Log.d("DiaryDetail", "뷰어 다운로드 완료: id=${attachment.fileId}, exists=${destFile.exists()}")
        }
        if (destFile.exists()) {
            onFileReady(attachment.fileId, destFile)
            fileReady = true
        } else {
            Log.w("DiaryDetail", "뷰어: 다운로드 후에도 파일 없음: id=${attachment.fileId}")
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        when {
            fileReady -> AsyncImage(
                model = cachedFile ?: destFile,
                contentDescription = attachment.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f, matchHeightConstraintsFirst = false),
            )
            isDownloading -> CircularProgressIndicator(color = Color.White)
            else -> Icon(
                Icons.Default.Image,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(64.dp),
            )
        }
    }
}

@Composable
private fun AttachmentDetailRow(
    attachment: Attachment,
    isDownloading: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isDownloading, onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isDownloading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                Icons.Default.AttachFile,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = attachment.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatAttachmentSize(attachment.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun attachmentCacheFile(cacheDir: File, attachment: Attachment): File {
    val ext = attachment.name.substringAfterLast('.', "")
    val name = if (ext.isNotEmpty()) "${attachment.fileId}.$ext" else attachment.fileId
    return File(cacheDir, name)
}

private fun formatAttachmentSize(bytes: Long): String = when {
    bytes < 1024L -> "${bytes}B"
    bytes < 1024L * 1024 -> "${bytes / 1024}KB"
    else -> "${"%.1f".format(bytes / (1024.0 * 1024))}MB"
}
