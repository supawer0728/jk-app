package com.jkapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jkapp.data.firestore.FirestoreRepository
import com.jkapp.data.firestore.FirestoreRepositoryImpl
import com.jkapp.data.model.Benchmark
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class BenchmarkViewModel(
    private val repository: FirestoreRepository = FirestoreRepositoryImpl(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<BenchmarkUiState>(BenchmarkUiState.Loading)
    val uiState: StateFlow<BenchmarkUiState> = _uiState.asStateFlow()

    // 저장/삭제 실패는 _uiState를 덮어쓰지 않는다. Firestore 스냅샷 리스너는 데이터가 실제로
    // 바뀔 때만 재발행되므로, 쓰기 실패(데이터 변화 없음) 시 Error로 덮으면 표가 사라진 채 고착된다.
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private var dataJob: Job? = null

    init {
        dataJob = viewModelScope.launch {
            repository.getBenchmarks()
                .map { BenchmarkUiState.Success(it) as BenchmarkUiState }
                .catch { e ->
                    emit(BenchmarkUiState.Error("벤치마크 목록을 불러오는 중 오류가 발생했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}"))
                }
                .collect { state -> _uiState.value = state }
        }
    }

    override fun onCleared() {
        super.onCleared()
        dataJob?.cancel()
    }

    fun saveBenchmark(benchmark: Benchmark) {
        viewModelScope.launch {
            runCatching { repository.upsertBenchmark(benchmark) }
                .onFailure { e ->
                    _actionError.value = "벤치마크 저장에 실패했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}"
                }
        }
    }

    fun deleteBenchmark(date: String) {
        viewModelScope.launch {
            runCatching { repository.deleteBenchmark(date) }
                .onFailure { e ->
                    _actionError.value = "벤치마크 삭제에 실패했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}"
                }
        }
    }

    fun consumeActionError() {
        _actionError.value = null
    }

    companion object {
        fun factory(): ViewModelProvider.Factory =
            viewModelFactory { initializer { BenchmarkViewModel() } }
    }
}
