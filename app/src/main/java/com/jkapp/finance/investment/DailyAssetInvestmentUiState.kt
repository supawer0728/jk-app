package com.jkapp.finance.investment


sealed interface DailyAssetInvestmentUiState {
    data object Loading : DailyAssetInvestmentUiState
    data class Success(val investments: List<DailyAssetInvestment>) : DailyAssetInvestmentUiState
    data class Error(val message: String) : DailyAssetInvestmentUiState
}
