package com.jkapp.ui

import com.jkapp.data.model.DailyAsset

sealed interface DailyAssetUiState {
    data object Loading : DailyAssetUiState
    data class Success(val dailyAssets: List<DailyAsset>) : DailyAssetUiState
    data class Error(val message: String) : DailyAssetUiState
}
