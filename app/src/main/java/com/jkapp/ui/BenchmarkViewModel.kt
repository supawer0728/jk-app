package com.jkapp.ui

import android.util.Log
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

    // 여러 날짜를 한 번에 삭제한다(전체 삭제/선택 삭제). 날짜별로 독립된 문서라 일부가 실패해도
    // 나머지는 그대로 반영하고, 실패한 날짜만 모아 하나의 에러로 보고한다.
    fun deleteBenchmarks(dates: List<String>) {
        viewModelScope.launch {
            val failures = dates.mapNotNull { date ->
                runCatching { repository.deleteBenchmark(date) }
                    .exceptionOrNull()
                    ?.let { date to it }
            }
            if (failures.isNotEmpty()) {
                val detail = failures.joinToString("\n") { (date, e) -> "$date: ${e.localizedMessage ?: "알 수 없는 오류"}" }
                _actionError.value = "일부 벤치마크를 삭제하지 못했습니다:\n$detail"
            }
        }
    }

    // 구글시트 붙여넣기 텍스트를 파싱한다. 파싱 실패 행은 원본 값을 그대로 로그에 남겨 디버깅에 활용한다.
    fun parsePasteText(text: String): List<ParsedBenchmarkRow> {
        val result = parseBenchmarkSheetPaste(text)
        result.filter { it.error != null }.forEach { row ->
            Log.w(TAG, "벤치마크 붙여넣기 파싱 실패: error=${row.error}, input=\"${row.rawLine}\"")
        }
        return result
    }

    // 붙여넣기로 여러 날짜의 벤치마크를 한 번에 저장한다. 날짜별로 독립된 문서라 일부가 실패해도
    // 나머지는 그대로 반영하고, 실패한 날짜만 모아 하나의 에러로 보고한다.
    fun importBenchmarks(benchmarks: List<Benchmark>) {
        viewModelScope.launch {
            val failures = benchmarks.mapNotNull { benchmark ->
                runCatching { repository.upsertBenchmark(benchmark) }
                    .exceptionOrNull()
                    ?.let { benchmark.date to it }
            }
            if (failures.isNotEmpty()) {
                val detail = failures.joinToString("\n") { (date, e) -> "$date: ${e.localizedMessage ?: "알 수 없는 오류"}" }
                _actionError.value = "일부 벤치마크를 저장하지 못했습니다:\n$detail"
            }
        }
    }

    companion object {
        private const val TAG = "BenchmarkViewModel"

        fun factory(): ViewModelProvider.Factory =
            viewModelFactory { initializer { BenchmarkViewModel() } }
    }
}
