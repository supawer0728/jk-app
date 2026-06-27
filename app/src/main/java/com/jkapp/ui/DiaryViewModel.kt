package com.jkapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jkapp.data.firestore.FirestoreRepository
import com.jkapp.data.firestore.FirestoreRepositoryImpl
import com.jkapp.data.model.CatRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class DiaryViewModel : ViewModel() {

    private val repository: FirestoreRepository = FirestoreRepositoryImpl()

    private val _uiState = MutableStateFlow<DiaryUiState>(DiaryUiState.Loading)
    val uiState: StateFlow<DiaryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.getRecordTypes(),
                repository.getRecords(),
            ) { types, records ->
                DiaryUiState.Success(records = records, recordTypes = types) as DiaryUiState
            }
                .catch { e -> emit(DiaryUiState.Error(e.message ?: "데이터를 불러오지 못했습니다.")) }
                .collect { _uiState.value = it }
        }
    }

    fun addRecord(record: CatRecord) {
        viewModelScope.launch {
            runCatching { repository.addRecord(record) }
                .onFailure { _uiState.value = DiaryUiState.Error(it.message ?: "저장에 실패했습니다.") }
        }
    }

    fun updateRecord(record: CatRecord) {
        viewModelScope.launch {
            runCatching { repository.updateRecord(record) }
                .onFailure { _uiState.value = DiaryUiState.Error(it.message ?: "저장에 실패했습니다.") }
        }
    }

    fun deleteRecord(firestoreId: String) {
        viewModelScope.launch {
            runCatching { repository.deleteRecord(firestoreId) }
                .onFailure { _uiState.value = DiaryUiState.Error(it.message ?: "삭제에 실패했습니다.") }
        }
    }

    companion object {
        fun todayDate(): String =
            LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        fun computeDayOfWeek(date: String): String =
            LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
                .dayOfWeek
                .getDisplayName(TextStyle.SHORT, Locale.KOREAN)
    }
}
