package com.jkapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.jkapp.data.firestore.FirestoreRepository
import com.jkapp.data.firestore.FirestoreRepositoryImpl
import com.jkapp.data.model.CatRecord
import kotlinx.coroutines.Job
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
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow<DiaryUiState>(DiaryUiState.Loading)
    val uiState: StateFlow<DiaryUiState> = _uiState.asStateFlow()

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

    companion object {
        fun todayDate(): String =
            LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        fun computeDayOfWeek(date: String): String =
            LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
                .dayOfWeek
                .getDisplayName(TextStyle.SHORT, Locale.KOREAN)
    }
}
