package com.jkapp.ui

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jkapp.auth.AuthRepository
import com.jkapp.auth.FirebaseAuthRepository
import com.jkapp.data.drive.DriveAuthRequiredException
import com.jkapp.data.drive.DriveRepository
import com.jkapp.data.firestore.FirestoreRepository
import com.jkapp.data.firestore.FirestoreRepositoryImpl
import com.jkapp.data.model.Attachment
import com.jkapp.data.model.CatRecord
import com.jkapp.data.model.CatRecordType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

private fun String.toYearMonthOrNull(): YearMonth? =
    runCatching { YearMonth.from(LocalDate.parse(this, DateTimeFormatter.ISO_LOCAL_DATE)) }.getOrNull()

class DiaryViewModel(
    private val repository: FirestoreRepository = FirestoreRepositoryImpl(),
    private val driveRepository: DriveRepository = DriveRepository.NoOp,
    authRepository: AuthRepository = FirebaseAuthRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<DiaryUiState>(DiaryUiState.Loading)
    val uiState: StateFlow<DiaryUiState> = _uiState.asStateFlow()

    private val _selectedTypeIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedTypeIds: StateFlow<Set<String>> = _selectedTypeIds.asStateFlow()

    private val _selectedYearMonth = MutableStateFlow<YearMonth?>(null)
    val selectedYearMonth: StateFlow<YearMonth?> = _selectedYearMonth.asStateFlow()

    val canMovePrevious: StateFlow<Boolean> = _uiState.combine(_selectedYearMonth) { state, month ->
        val months = (state as? DiaryUiState.Success)?.availableMonths ?: emptyList()
        month != null && months.any { it < month }
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    val canMoveNext: StateFlow<Boolean> = _uiState.combine(_selectedYearMonth) { state, month ->
        val months = (state as? DiaryUiState.Success)?.availableMonths ?: emptyList()
        month != null && months.any { it > month }
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    private val _pendingAttachments = MutableStateFlow<List<Attachment>>(emptyList())
    val pendingAttachments: StateFlow<List<Attachment>> = _pendingAttachments.asStateFlow()

    private val _isUploadingAttachment = MutableStateFlow(false)
    val isUploadingAttachment: StateFlow<Boolean> = _isUploadingAttachment.asStateFlow()

    private val _downloadingAttachmentIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingAttachmentIds: StateFlow<Set<String>> = _downloadingAttachmentIds.asStateFlow()

    private val _attachmentUploadError = MutableStateFlow<String?>(null)
    val attachmentUploadError: StateFlow<String?> = _attachmentUploadError.asStateFlow()

    private val _driveAuthRecoveryIntent = MutableStateFlow<Intent?>(null)
    val driveAuthRecoveryIntent: StateFlow<Intent?> = _driveAuthRecoveryIntent.asStateFlow()

    private val _saveCompleted = MutableStateFlow(false)
    val saveCompleted: StateFlow<Boolean> = _saveCompleted.asStateFlow()

    fun clearAttachmentUploadError() {
        _attachmentUploadError.value = null
    }

    fun clearDriveAuthRecoveryIntent() {
        _driveAuthRecoveryIntent.value = null
    }

    fun consumeSaveCompleted() {
        _saveCompleted.value = false
    }

    private var dataJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.observeAuthState().collect { isLoggedIn ->
                if (isLoggedIn) {
                    startDataCollection()
                } else {
                    dataJob?.cancel()
                    _uiState.value = DiaryUiState.Loading
                    _selectedYearMonth.value = null
                }
            }
        }
        viewModelScope.launch {
            authRepository.observeCurrentUserEmail().collect { email ->
                Log.d(TAG, "Drive 계정 이메일: $email")
                email?.let { driveRepository.setAccount(it) }
            }
        }
    }

    private fun startDataCollection() {
        dataJob?.cancel()
        dataJob = viewModelScope.launch {
            combine(
                repository.getRecordTypes().onStart { emit(emptyList()) },
                repository.getRecords(),
            ) { types, records ->
                val availableMonths = records
                    .mapNotNull { it.date.toYearMonthOrNull() }
                    .distinct()
                    .sortedDescending()
                DiaryUiState.Success(
                    records = records,
                    recordTypes = types,
                    availableMonths = availableMonths,
                ) as DiaryUiState
            }
                .catch { e ->
                    emit(DiaryUiState.Error("기록을 불러오는 중 오류가 발생했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}"))
                }
                .collect { state ->
                    _uiState.value = state
                    if (state is DiaryUiState.Success) {
                        reconcileSelectedMonth(state.availableMonths)
                    }
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        dataJob?.cancel()
    }

    // ── Drive 인증 ──────────────────────────────────────────────────────────────

    fun onDriveAccountSelected(accountName: String) {
        driveRepository.setAccount(accountName)
    }

    // ── 첨부파일 관리 ────────────────────────────────────────────────────────────

    // 저장 전 로컬에만 보관하는 파일 정보
    private data class PendingLocalFile(
        val tempId: String,
        val openStream: () -> InputStream?,
        val fileName: String,
        val mimeType: String,
        val size: Long,
    )

    // Drive 인증 실패 후 재시도 대기 중인 업로드
    private data class PendingRetryUpload(
        val tempId: String,
        val openStream: () -> InputStream?,
        val fileName: String,
        val mimeType: String,
        val recordId: String,
    )

    private data class UploadOutcome(
        val uploaded: List<Attachment>,
        val authRequired: Boolean,
        val errors: List<String> = emptyList(),
    )

    private sealed class FileUploadResult {
        data class Success(val attachment: Attachment) : FileUploadResult()
        data class AuthRequired(
            val file: PendingLocalFile,
            val exception: DriveAuthRequiredException,
        ) : FileUploadResult()
        data class Failed(val error: String) : FileUploadResult()
    }

    private val pendingLocalFiles = mutableListOf<PendingLocalFile>()
    private val pendingRetries = mutableListOf<PendingRetryUpload>()

    // 재시도 완료 후 Firestore 업데이트할 record 컨텍스트
    private var retryCompletionRecord: CatRecord? = null

    fun addLocalFile(openStream: () -> InputStream?, fileName: String, mimeType: String, size: Long) {
        val tempId = "local-${UUID.randomUUID()}"
        pendingLocalFiles.add(PendingLocalFile(tempId, openStream, fileName, mimeType, size))
        _pendingAttachments.update { it + Attachment(tempId, fileName, mimeType, size) }
    }

    fun retryFailedUploads() {
        val retries = pendingRetries.toList()
        pendingRetries.clear()

        viewModelScope.launch {
            _isUploadingAttachment.value = true
            val uploaded = mutableListOf<Attachment>()

            for ((index, retry) in retries.withIndex()) {
                val stream = retry.openStream()
                if (stream == null) {
                    _pendingAttachments.update { it.filter { a -> a.fileId != retry.tempId } }
                    continue
                }
                try {
                    val attachment = driveRepository.uploadFile(retry.recordId, stream, retry.fileName, retry.mimeType)
                    _pendingAttachments.update { list ->
                        list.map { if (it.fileId == retry.tempId) attachment else it }
                    }
                    uploaded.add(attachment)
                } catch (e: DriveAuthRequiredException) {
                    Log.e(TAG, "Drive 재업로드 인증 필요: fileName=${retry.fileName}", e)
                    pendingRetries.addAll(retries.drop(index))
                    _driveAuthRecoveryIntent.value = e.recoveryIntent
                    _isUploadingAttachment.value = false
                    return@launch
                } catch (e: Exception) {
                    Log.e(TAG, "Drive 재업로드 실패: fileName=${retry.fileName}", e)
                    _pendingAttachments.update { it.filter { a -> a.fileId != retry.tempId } }
                    _attachmentUploadError.value = "파일 업로드에 실패했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}"
                }
            }

            // 모든 재시도 완료 → Firestore 업데이트
            retryCompletionRecord?.let { ctx ->
                retryCompletionRecord = null
                val finalRecord = ctx.copy(attachments = ctx.attachments + uploaded)
                runCatching { repository.updateRecord(finalRecord) }
                    .onSuccess {
                        _pendingAttachments.value = emptyList()
                        _saveCompleted.value = true
                    }
                    .onFailure { e ->
                        Log.e(TAG, "재시도 후 record 업데이트 실패", e)
                        _uiState.value = DiaryUiState.Error("기록 업데이트에 실패했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}")
                    }
            } ?: run {
                _pendingAttachments.value = emptyList()
            }

            _isUploadingAttachment.value = false
        }
    }

    fun cancelFailedUploads() {
        val failedIds = pendingRetries.map { it.tempId }.toSet()
        pendingRetries.clear()
        retryCompletionRecord = null
        _pendingAttachments.update { list -> list.filter { it.fileId !in failedIds } }
    }

    fun removePendingAttachment(fileId: String) {
        _pendingAttachments.update { it.filter { a -> a.fileId != fileId } }
        pendingLocalFiles.removeAll { it.tempId == fileId }
        pendingRetries.removeAll { it.tempId == fileId }
    }

    fun cancelPendingAttachments() {
        _pendingAttachments.value = emptyList()
        pendingLocalFiles.clear()
        pendingRetries.clear()
        retryCompletionRecord = null
    }

    suspend fun downloadAttachment(fileId: String, destFile: java.io.File) {
        _downloadingAttachmentIds.update { it + fileId }
        try {
            withContext(Dispatchers.IO) {
                driveRepository.downloadFile(fileId).use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
        } catch (e: DriveAuthRequiredException) {
            Log.w(TAG, "Drive 다운로드 인증 필요: fileId=$fileId")
            _driveAuthRecoveryIntent.value = e.recoveryIntent
        } catch (e: Exception) {
            Log.e(TAG, "Drive 다운로드 실패: fileId=$fileId", e)
            throw e
        } finally {
            _downloadingAttachmentIds.update { it - fileId }
        }
    }

    // recordId 기준으로 로컬 파일들을 Drive에 병렬 업로드한다.
    // DriveAuthRequiredException 발생 파일은 pendingRetries에 추가하고 authRequired=true 반환.
    private suspend fun uploadFilesToDrive(
        recordId: String,
        localFiles: List<PendingLocalFile>,
    ): UploadOutcome {
        if (localFiles.isEmpty()) return UploadOutcome(emptyList(), false)

        val results: List<FileUploadResult> = coroutineScope {
            localFiles.map { file ->
                async {
                    val stream = file.openStream()
                    if (stream == null) {
                        _pendingAttachments.update { it.filter { a -> a.fileId != file.tempId } }
                        return@async FileUploadResult.Failed("파일을 열 수 없습니다: ${file.fileName}")
                    }
                    try {
                        val attachment = driveRepository.uploadFile(recordId, stream, file.fileName, file.mimeType)
                        _pendingAttachments.update { list ->
                            list.map { if (it.fileId == file.tempId) attachment else it }
                        }
                        FileUploadResult.Success(attachment)
                    } catch (e: DriveAuthRequiredException) {
                        Log.e(TAG, "Drive 업로드 인증 필요: fileName=${file.fileName}", e)
                        FileUploadResult.AuthRequired(file, e)
                    } catch (e: Exception) {
                        Log.e(TAG, "Drive 업로드 실패: fileName=${file.fileName}", e)
                        _pendingAttachments.update { it.filter { a -> a.fileId != file.tempId } }
                        FileUploadResult.Failed("파일 업로드에 실패했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}")
                    }
                }
            }.awaitAll()
        }

        val errors = results.filterIsInstance<FileUploadResult.Failed>().map { it.error }
        val authFailures = results.filterIsInstance<FileUploadResult.AuthRequired>()
        if (authFailures.isNotEmpty()) {
            authFailures.forEach { r ->
                pendingRetries.add(PendingRetryUpload(r.file.tempId, r.file.openStream, r.file.fileName, r.file.mimeType, recordId))
            }
            _driveAuthRecoveryIntent.value = authFailures.first().exception.recoveryIntent
            val uploaded = results.filterIsInstance<FileUploadResult.Success>().map { it.attachment }
            return UploadOutcome(uploaded = uploaded, authRequired = true, errors = errors)
        }

        val uploaded = results.filterIsInstance<FileUploadResult.Success>().map { it.attachment }
        return UploadOutcome(uploaded = uploaded, authRequired = false, errors = errors)
    }

    private fun deleteFilesFromDrive(fileIds: List<String>, label: String) {
        fileIds.forEach { fileId ->
            viewModelScope.launch {
                runCatching { driveRepository.deleteFile(fileId) }
                    .onFailure { e -> Log.w(TAG, "Drive GC 실패 ($label): fileId=$fileId", e) }
            }
        }
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────────

    fun addRecord(record: CatRecord) {
        val localFiles = pendingLocalFiles.toList()
        pendingLocalFiles.clear()

        viewModelScope.launch {
            _isUploadingAttachment.value = true
            runCatching {
                // 1. Firestore에 첨부파일 없이 저장 → firestoreId 반환
                val firestoreId = repository.addRecord(record.copy(attachments = emptyList()))

                // 2. firestoreId 기준으로 Drive 업로드
                val outcome = uploadFilesToDrive(firestoreId, localFiles)

                if (outcome.authRequired) {
                    // 인증 완료 후 재시도 시 Firestore 업데이트를 위해 컨텍스트 보관
                    retryCompletionRecord = record.copy(firestoreId = firestoreId, attachments = outcome.uploaded)
                } else {
                    if (outcome.errors.isNotEmpty()) {
                        _attachmentUploadError.value = outcome.errors.joinToString("\n")
                    }
                    // 3. 업로드된 첨부파일로 Firestore 업데이트
                    if (outcome.uploaded.isNotEmpty()) {
                        repository.updateRecord(record.copy(firestoreId = firestoreId, attachments = outcome.uploaded))
                    }
                    _pendingAttachments.value = emptyList()
                    _saveCompleted.value = true
                }
            }.onFailure { e ->
                _uiState.value = DiaryUiState.Error(
                    "새 기록 저장에 실패했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}"
                )
            }
            _isUploadingAttachment.value = false
        }
    }

    fun updateRecord(original: CatRecord, updated: CatRecord) {
        val localFiles = pendingLocalFiles.toList()
        pendingLocalFiles.clear()

        viewModelScope.launch {
            _isUploadingAttachment.value = true
            runCatching {
                val firestoreId = updated.firestoreId
                    ?: throw IllegalArgumentException("수정할 기록의 ID가 없습니다")

                // 1. 새 파일들을 firestoreId 기준으로 Drive 업로드
                val outcome = uploadFilesToDrive(firestoreId, localFiles)

                val finalAttachments = updated.attachments + outcome.uploaded

                if (outcome.authRequired) {
                    // 인증 완료 후 재시도 시 Firestore 업데이트를 위해 컨텍스트 보관
                    retryCompletionRecord = updated.copy(attachments = finalAttachments)
                } else {
                    if (outcome.errors.isNotEmpty()) {
                        _attachmentUploadError.value = outcome.errors.joinToString("\n")
                    }
                    // 2. Firestore 업데이트
                    val finalRecord = updated.copy(attachments = finalAttachments)
                    repository.updateRecord(finalRecord)

                    // 3. 삭제된 첨부파일 Drive GC (병렬)
                    val removedIds = original.attachments
                        .filter { orig -> finalRecord.attachments.none { it.fileId == orig.fileId } }
                        .map { it.fileId }
                    deleteFilesFromDrive(removedIds, "record 수정")

                    _pendingAttachments.value = emptyList()
                    _saveCompleted.value = true
                }
            }.onFailure { e ->
                _uiState.value = DiaryUiState.Error(
                    "기록 수정에 실패했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}"
                )
            }
            _isUploadingAttachment.value = false
        }
    }

    fun deleteRecord(firestoreId: String) {
        val record = (uiState.value as? DiaryUiState.Success)
            ?.records?.find { it.firestoreId == firestoreId }
        viewModelScope.launch {
            runCatching { repository.deleteRecord(firestoreId) }
                .onSuccess {
                    deleteFilesFromDrive(
                        record?.attachments?.map { it.fileId } ?: emptyList(),
                        "record 삭제",
                    )
                }
                .onFailure {
                    _uiState.value = DiaryUiState.Error(
                        "기록 삭제에 실패했습니다: ${it.localizedMessage ?: "알 수 없는 오류"}"
                    )
                }
        }
    }

    fun toggleTypeFilter(id: String) {
        _selectedTypeIds.update { toggleInSet(id, it) }
    }

    fun clearTypeFilter() {
        _selectedTypeIds.update { emptySet() }
    }

    fun selectYearMonth(month: YearMonth) {
        _selectedYearMonth.value = month
    }

    fun moveToPreviousMonth() {
        val state = uiState.value as? DiaryUiState.Success ?: return
        val current = _selectedYearMonth.value ?: return
        state.availableMonths.filter { it < current }.maxOrNull()?.let {
            _selectedYearMonth.value = it
        }
    }

    fun moveToNextMonth() {
        val state = uiState.value as? DiaryUiState.Success ?: return
        val current = _selectedYearMonth.value ?: return
        state.availableMonths.filter { it > current }.minOrNull()?.let {
            _selectedYearMonth.value = it
        }
    }

    private fun reconcileSelectedMonth(availableMonths: List<YearMonth>) {
        val current = _selectedYearMonth.value
        if (availableMonths.isEmpty()) {
            _selectedYearMonth.value = null
        } else if (current == null || current !in availableMonths) {
            _selectedYearMonth.value = availableMonths.first()
        }
    }

    fun addRecordType(type: CatRecordType) {
        val existingIds = (uiState.value as? DiaryUiState.Success)?.recordTypes?.map { it.id }.orEmpty()
        val error = validateNewRecordType(type, existingIds)
        if (error != null) {
            _uiState.value = DiaryUiState.Error(error)
            return
        }
        viewModelScope.launch {
            runCatching { repository.addRecordType(type) }
                .onFailure {
                    _uiState.value = DiaryUiState.Error(
                        "기록유형 저장에 실패했습니다: ${it.localizedMessage ?: "알 수 없는 오류"}"
                    )
                }
        }
    }

    fun updateRecordType(type: CatRecordType) {
        viewModelScope.launch {
            runCatching { repository.updateRecordType(type) }
                .onFailure {
                    _uiState.value = DiaryUiState.Error(
                        "기록유형 수정에 실패했습니다: ${it.localizedMessage ?: "알 수 없는 오류"}"
                    )
                }
        }
    }

    fun deleteRecordType(docId: String) {
        val state = uiState.value as? DiaryUiState.Success ?: return
        val type = state.recordTypes.find { it.docId == docId }
        val error = validateDeleteRecordType(type?.id)
        if (error != null) {
            _uiState.value = DiaryUiState.Error(error)
            return
        }
        val affectedRecordIds = state.records
            .filter { it.recordType == type?.id }
            .mapNotNull { it.firestoreId }
        viewModelScope.launch {
            runCatching {
                repository.deleteRecordTypeAndReassignRecords(docId, affectedRecordIds, FALLBACK_RECORD_TYPE_ID)
            }.onFailure {
                _uiState.value = DiaryUiState.Error(
                    "기록유형 삭제에 실패했습니다: ${it.localizedMessage ?: "알 수 없는 오류"}"
                )
            }
        }
    }

    companion object {
        private const val TAG = "DiaryViewModel"
        val SYSTEM_TYPE_IDS = setOf("DAILY_NOTE", "HOSPITAL_VISIT")

        fun factory(driveRepository: DriveRepository): ViewModelProvider.Factory =
            viewModelFactory { initializer { DiaryViewModel(driveRepository = driveRepository) } }
        const val FALLBACK_RECORD_TYPE_ID = "DAILY_NOTE"

        fun toggleInSet(id: String, current: Set<String>): Set<String> =
            if (id in current) current - id else current + id

        fun filterRecords(records: List<CatRecord>, selectedTypeIds: Set<String>): List<CatRecord> {
            if (selectedTypeIds.isEmpty()) return records
            val normalizedSelected = selectedTypeIds.map { it.trim().lowercase() }.toSet()
            return records.filter { it.recordType.trim().lowercase() in normalizedSelected }
        }

        fun filterRecordsByMonth(records: List<CatRecord>, yearMonth: YearMonth?): List<CatRecord> {
            if (yearMonth == null) return records
            return records.filter { it.date.toYearMonthOrNull() == yearMonth }
        }

        fun todayDate(): String =
            LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        fun computeDayOfWeek(date: String): String =
            LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
                .dayOfWeek
                .getDisplayName(TextStyle.SHORT, Locale.KOREAN)

        fun validateNewRecordType(type: CatRecordType, existingIds: List<String>): String? {
            if (type.id in SYSTEM_TYPE_IDS) return "시스템 필수 유형 ID는 사용할 수 없습니다."
            if (type.id in existingIds) return "이미 존재하는 기록유형 ID입니다: ${type.id}"
            return null
        }

        fun validateDeleteRecordType(typeId: String?): String? {
            if (typeId in SYSTEM_TYPE_IDS) return "시스템 필수 유형은 삭제할 수 없습니다."
            return null
        }
    }
}
