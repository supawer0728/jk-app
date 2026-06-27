package com.jkapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jkapp.data.drive.DriveRepository
import com.jkapp.data.drive.DriveRepositoryImpl
import com.jkapp.data.model.AkiHealthRecord
import com.jkapp.data.model.HealthRecord
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class DiaryViewModel(app: Application) : AndroidViewModel(app) {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    internal val repository: DriveRepository = DriveRepositoryImpl(moshi)

    private val _uiState = MutableStateFlow<DiaryUiState>(DiaryUiState.NeedsAuth)
    val uiState: StateFlow<DiaryUiState> = _uiState.asStateFlow()

    private var accessToken: String? = null
    val isAuthorized: Boolean get() = accessToken != null

    fun onDriveAuthSuccess(token: String) {
        accessToken = token
        loadRecords()
    }

    fun onDriveAuthFailed(reason: String? = null) {
        _uiState.value = DiaryUiState.Error(reason ?: "Drive 인증에 실패했습니다.")
    }

    fun loadRecords() {
        val token = accessToken ?: return
        viewModelScope.launch {
            _uiState.value = DiaryUiState.Loading
            runCatching { repository.fetchRecord(token) }
                .onSuccess { _uiState.value = DiaryUiState.Success(it) }
                .onFailure { _uiState.value = DiaryUiState.Error(it.message ?: "데이터를 불러오지 못했습니다.") }
        }
    }

    fun addRecord(record: HealthRecord) = modifyRecords { records ->
        (listOf(record) + records).sortedByDescending { it.date }
    }

    fun updateRecord(date: String, updated: HealthRecord) = modifyRecords { records ->
        records.map { if (it.date == date) updated else it }
    }

    fun deleteRecord(date: String) = modifyRecords { records ->
        records.filterNot { it.date == date }
    }

    private fun modifyRecords(transform: (List<HealthRecord>) -> List<HealthRecord>) {
        val token = accessToken ?: return
        val current = (_uiState.value as? DiaryUiState.Success)?.data ?: return
        val updated = current.copy(records = transform(current.records))
        viewModelScope.launch {
            _uiState.value = DiaryUiState.Loading
            runCatching { repository.saveRecord(token, updated) }
                .onSuccess { _uiState.value = DiaryUiState.Success(updated) }
                .onFailure { _uiState.value = DiaryUiState.Error(it.message ?: "저장에 실패했습니다.") }
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
