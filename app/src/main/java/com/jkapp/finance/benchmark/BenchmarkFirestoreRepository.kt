package com.jkapp.finance.benchmark

import kotlinx.coroutines.flow.Flow

interface BenchmarkFirestoreRepository {
    fun getBenchmarks(): Flow<List<Benchmark>>
    suspend fun upsertBenchmark(benchmark: Benchmark)
    suspend fun upsertBenchmarks(benchmarks: List<Benchmark>)
    suspend fun deleteBenchmark(date: String)
    suspend fun deleteBenchmarks(dates: List<String>)

    // getBenchmarks()가 반환하는 목록은 역직렬화에 실패한 문서를 걸러낸 결과이므로, 그 목록에서 뽑은
    // 날짜만으로 삭제하면 손상된 문서가 영구히 남는다. 컬렉션의 실제 문서를 다시 조회해 전부 삭제한다.
    suspend fun deleteAllBenchmarks()
}
