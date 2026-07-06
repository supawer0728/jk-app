package com.jkapp.finance.benchmark


sealed interface BenchmarkUiState {
    data object Loading : BenchmarkUiState
    data class Success(val benchmarks: List<Benchmark>) : BenchmarkUiState
    data class Error(val message: String) : BenchmarkUiState
}
