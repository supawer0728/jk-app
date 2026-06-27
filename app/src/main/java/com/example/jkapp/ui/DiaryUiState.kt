package com.example.jkapp.ui

import com.example.jkapp.data.model.AkiHealthRecord

sealed class DiaryUiState {
    object NeedsAuth : DiaryUiState()
    object Loading : DiaryUiState()
    data class Success(val data: AkiHealthRecord) : DiaryUiState()
    data class Error(val message: String) : DiaryUiState()
}
