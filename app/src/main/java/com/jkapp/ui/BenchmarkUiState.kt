package com.jkapp.ui

import com.jkapp.data.model.Benchmark

sealed interface BenchmarkUiState {
    data object Loading : BenchmarkUiState
    data class Success(val benchmarks: List<Benchmark>) : BenchmarkUiState
    data class Error(val message: String) : BenchmarkUiState
}
