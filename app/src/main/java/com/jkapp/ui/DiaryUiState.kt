package com.jkapp.ui

import com.jkapp.data.model.CatRecord
import com.jkapp.data.model.CatRecordType

sealed interface DiaryUiState {
    data object Loading : DiaryUiState
    data class Success(
        val records: List<CatRecord>,
        val recordTypes: List<CatRecordType>,
    ) : DiaryUiState
    data class Error(val message: String) : DiaryUiState
}
