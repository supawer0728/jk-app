package com.jkapp.data.firestore

import com.jkapp.data.model.Benchmark
import com.jkapp.data.model.CatRecord
import com.jkapp.data.model.CatRecordType
import com.jkapp.data.model.DailyAsset
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

    fun getBenchmarks(): Flow<List<Benchmark>>
    suspend fun upsertBenchmark(benchmark: Benchmark)
    suspend fun upsertBenchmarks(benchmarks: List<Benchmark>)
    suspend fun deleteBenchmark(date: String)
    suspend fun deleteBenchmarks(dates: List<String>)
}
