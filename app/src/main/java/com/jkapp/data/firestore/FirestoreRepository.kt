package com.jkapp.data.firestore

import com.jkapp.data.model.Benchmark
import com.jkapp.data.model.CatRecord
import com.jkapp.data.model.CatRecordType
import com.jkapp.data.model.DailyAsset
import com.jkapp.data.model.DailyAssetInvestment
import kotlinx.coroutines.flow.Flow

interface FirestoreRepository {
    fun getRecordTypes(): Flow<List<CatRecordType>>
    fun getRecords(): Flow<List<CatRecord>>
    suspend fun addRecord(record: CatRecord): String
    suspend fun updateRecord(record: CatRecord)
    suspend fun deleteRecord(firestoreId: String)
    suspend fun addRecordType(type: CatRecordType)
    suspend fun updateRecordType(type: CatRecordType)
    suspend fun deleteRecordTypeAndReassignRecords(typeDocId: String, affectedRecordIds: List<String>, fallbackTypeId: String)

    fun getDailyAssets(): Flow<List<DailyAsset>>
    suspend fun upsertDailyAsset(asset: DailyAsset)
    suspend fun deleteDailyAsset(date: String)

    fun getDailyAssetInvestments(): Flow<List<DailyAssetInvestment>>
    suspend fun upsertDailyAssetInvestment(investment: DailyAssetInvestment)
    suspend fun deleteDailyAssetInvestment(date: String, owner: String)

    fun getBenchmarks(): Flow<List<Benchmark>>
    suspend fun upsertBenchmark(benchmark: Benchmark)
    suspend fun upsertBenchmarks(benchmarks: List<Benchmark>)
    suspend fun deleteBenchmark(date: String)
    suspend fun deleteBenchmarks(dates: List<String>)

    // getBenchmarks()가 반환하는 목록은 역직렬화에 실패한 문서를 걸러낸 결과이므로, 그 목록에서 뽑은
    // 날짜만으로 삭제하면 손상된 문서가 영구히 남는다. 컬렉션의 실제 문서를 다시 조회해 전부 삭제한다.
    suspend fun deleteAllBenchmarks()
}
