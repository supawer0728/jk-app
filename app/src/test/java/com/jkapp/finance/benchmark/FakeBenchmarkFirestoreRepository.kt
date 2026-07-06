package com.jkapp.finance.benchmark

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeBenchmarkFirestoreRepository : BenchmarkFirestoreRepository {

    private val _benchmarks = MutableStateFlow<List<Benchmark>>(emptyList())

    var upsertBenchmarkError: Throwable? = null
    var deleteBenchmarkError: Throwable? = null
    var deleteAllBenchmarksError: Throwable? = null
    // importBenchmarks/deleteBenchmarks의 부분 실패(일부 날짜만 실패)를 재현하기 위한 훅.
    var upsertBenchmarkErrorDates: Set<String> = emptySet()
    var deleteBenchmarkErrorDates: Set<String> = emptySet()

    fun setBenchmarks(benchmarks: List<Benchmark>) { _benchmarks.value = benchmarks }

    override fun getBenchmarks(): Flow<List<Benchmark>> = _benchmarks

    override suspend fun upsertBenchmark(benchmark: Benchmark) {
        upsertBenchmarkError?.let { throw it }
        if (benchmark.date in upsertBenchmarkErrorDates) throw RuntimeException("저장 실패: ${benchmark.date}")
        val existingIndex = _benchmarks.value.indexOfFirst { it.date == benchmark.date }
        _benchmarks.value = if (existingIndex >= 0) {
            _benchmarks.value.mapIndexed { index, existing -> if (index == existingIndex) benchmark else existing }
        } else {
            _benchmarks.value + benchmark
        }
    }

    // 실제 Firestore 배치처럼 원자적으로 동작한다: 하나라도 실패 대상이면 아무것도 저장하지 않고 예외를 던진다.
    override suspend fun upsertBenchmarks(benchmarks: List<Benchmark>) {
        upsertBenchmarkError?.let { throw it }
        if (benchmarks.any { it.date in upsertBenchmarkErrorDates }) {
            throw RuntimeException("저장 실패: ${benchmarks.filter { it.date in upsertBenchmarkErrorDates }.map { it.date }}")
        }
        var updated = _benchmarks.value
        benchmarks.forEach { benchmark ->
            val existingIndex = updated.indexOfFirst { it.date == benchmark.date }
            updated = if (existingIndex >= 0) {
                updated.mapIndexed { index, existing -> if (index == existingIndex) benchmark else existing }
            } else {
                updated + benchmark
            }
        }
        _benchmarks.value = updated
    }

    override suspend fun deleteBenchmark(date: String) {
        deleteBenchmarkError?.let { throw it }
        if (date in deleteBenchmarkErrorDates) throw RuntimeException("삭제 실패: $date")
        _benchmarks.value = _benchmarks.value.filter { it.date != date }
    }

    // 실제 Firestore 배치처럼 원자적으로 동작한다: 하나라도 실패 대상이면 아무것도 지우지 않고 예외를 던진다.
    override suspend fun deleteBenchmarks(dates: List<String>) {
        deleteBenchmarkError?.let { throw it }
        if (dates.any { it in deleteBenchmarkErrorDates }) {
            throw RuntimeException("삭제 실패: ${dates.filter { it in deleteBenchmarkErrorDates }}")
        }
        _benchmarks.value = _benchmarks.value.filter { it.date !in dates }
    }

    override suspend fun deleteAllBenchmarks() {
        deleteAllBenchmarksError?.let { throw it }
        _benchmarks.value = emptyList()
    }
}
