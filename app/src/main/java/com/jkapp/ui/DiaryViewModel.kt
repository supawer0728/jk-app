package com.jkapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.jkapp.data.firestore.FirestoreRepository
import com.jkapp.data.firestore.FirestoreRepositoryImpl
import com.jkapp.data.model.CatRecord
import com.jkapp.data.model.CatRecordType
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class DiaryViewModel : ViewModel() {

    private val repository: FirestoreRepository = FirestoreRepositoryImpl()
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow<DiaryUiState>(DiaryUiState.Loading)
    val uiState: StateFlow<DiaryUiState> = _uiState.asStateFlow()

    private val _selectedTypeIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedTypeIds: StateFlow<Set<String>> = _selectedTypeIds.asStateFlow()

    private var dataJob: Job? = null
    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        if (firebaseAuth.currentUser != null) {
            startDataCollection()
        } else {
            dataJob?.cancel()
            _uiState.value = DiaryUiState.Loading
        }
    }

    init {
        auth.addAuthStateListener(authListener)
    }

    private fun startDataCollection() {
        dataJob?.cancel()
        dataJob = viewModelScope.launch {
            combine(
                repository.getRecordTypes(),
                repository.getRecords(),
            ) { types, records ->
                DiaryUiState.Success(records = records, recordTypes = types) as DiaryUiState
            }
                .catch { e -> 
                    emit(DiaryUiState.Error("기록을 불러오는 중 오류가 발생했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}")) 
                }
                .collect { _uiState.value = it }
        }
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authListener)
        dataJob?.cancel()
    }

    fun addRecord(record: CatRecord) {
        viewModelScope.launch {
            runCatching { repository.addRecord(record) }
                .onFailure { 
                    _uiState.value = DiaryUiState.Error("새 기록 저장에 실패했습니다: ${it.localizedMessage ?: "알 수 없는 오류"}") 
                }
        }
    }

    fun updateRecord(record: CatRecord) {
        viewModelScope.launch {
            runCatching { repository.updateRecord(record) }
                .onFailure { 
                    _uiState.value = DiaryUiState.Error("기록 수정에 실패했습니다: ${it.localizedMessage ?: "알 수 없는 오류"}") 
                }
        }
    }

    fun deleteRecord(firestoreId: String) {
        viewModelScope.launch {
            runCatching { repository.deleteRecord(firestoreId) }
                .onFailure {
                    _uiState.value = DiaryUiState.Error("기록 삭제에 실패했습니다: ${it.localizedMessage ?: "알 수 없는 오류"}")
                }
        }
    }

    fun toggleTypeFilter(id: String) {
        _selectedTypeIds.update { toggleInSet(id, it) }
    }

    fun clearTypeFilter() {
        _selectedTypeIds.update { emptySet() }
    }

    fun addRecordType(type: CatRecordType) {
        if (type.id in SYSTEM_TYPE_IDS) {
            _uiState.value = DiaryUiState.Error("시스템 필수 유형 ID는 사용할 수 없습니다.")
            return
        }
        viewModelScope.launch {
            runCatching { repository.addRecordType(type) }
                .onFailure {
                    _uiState.value = DiaryUiState.Error("기록유형 저장에 실패했습니다: ${it.localizedMessage ?: "알 수 없는 오류"}")
                }
        }
    }

    fun updateRecordType(type: CatRecordType) {
        viewModelScope.launch {
            runCatching { repository.updateRecordType(type) }
                .onFailure {
                    _uiState.value = DiaryUiState.Error("기록유형 수정에 실패했습니다: ${it.localizedMessage ?: "알 수 없는 오류"}")
                }
        }
    }

    fun deleteRecordType(docId: String) {
        val typeId = (uiState.value as? DiaryUiState.Success)
            ?.recordTypes?.find { it.docId == docId || it.id == docId }?.id
        val isSystemType = docId in SYSTEM_TYPE_IDS || typeId in SYSTEM_TYPE_IDS
        if (typeId == null || isSystemType) {
            if (isSystemType) _uiState.value = DiaryUiState.Error("시스템 필수 유형은 삭제할 수 없습니다.")
            return
        }
        viewModelScope.launch {
            runCatching { repository.deleteRecordType(docId) }
                .onFailure {
                    _uiState.value = DiaryUiState.Error("기록유형 삭제에 실패했습니다: ${it.localizedMessage ?: "알 수 없는 오류"}")
                }
        }
    }

    companion object {
        val SYSTEM_TYPE_IDS = setOf("DAILY_NOTE", "HOSPITAL_VISIT")

        fun toggleInSet(id: String, current: Set<String>): Set<String> =
            if (id in current) current - id else current + id

        fun filterRecords(records: List<CatRecord>, selectedTypeIds: Set<String>): List<CatRecord> {
            if (selectedTypeIds.isEmpty()) return records
            val normalizedSelected = selectedTypeIds.map { it.trim().lowercase() }.toSet()
            return records.filter { it.recordType.trim().lowercase() in normalizedSelected }
        }

        fun todayDate(): String =
            LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        fun computeDayOfWeek(date: String): String =
            LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
                .dayOfWeek
                .getDisplayName(TextStyle.SHORT, Locale.KOREAN)
    }
}
