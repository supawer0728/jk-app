package com.jkapp.data.firestore

import com.jkapp.data.model.Benchmark
import com.jkapp.data.model.CatRecord
import com.jkapp.data.model.CatRecordType
import com.jkapp.data.model.DailyAsset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeFirestoreRepository : FirestoreRepository {

    private val _recordTypes = MutableStateFlow<List<CatRecordType>>(emptyList())
    private val _records = MutableStateFlow<List<CatRecord>>(emptyList())
    private val _dailyAssets = MutableStateFlow<List<DailyAsset>>(emptyList())
    private val _benchmarks = MutableStateFlow<List<Benchmark>>(emptyList())

    var addRecordError: Throwable? = null
    var updateRecordError: Throwable? = null
    var deleteRecordError: Throwable? = null
    var addRecordTypeError: Throwable? = null
    var updateRecordTypeError: Throwable? = null
    var deleteRecordTypeError: Throwable? = null
    var upsertDailyAssetError: Throwable? = null
    var deleteDailyAssetError: Throwable? = null
    var upsertBenchmarkError: Throwable? = null
    var deleteBenchmarkError: Throwable? = null
    var deleteAllBenchmarksError: Throwable? = null
    // importBenchmarks/deleteBenchmarks의 부분 실패(일부 날짜만 실패)를 재현하기 위한 훅.
    var upsertBenchmarkErrorDates: Set<String> = emptySet()
    var deleteBenchmarkErrorDates: Set<String> = emptySet()

    // 테스트에서 실제 Firestore 네트워크 왕복(suspension)을 흉내내기 위한 훅.
    // 동시 호출 시 뮤텍스로 직렬화되는지 검증하는 데 사용한다.
    var onUpsertDailyAsset: (suspend () -> Unit)? = null

    fun setRecordTypes(types: List<CatRecordType>) { _recordTypes.value = types }
    fun setRecords(records: List<CatRecord>) { _records.value = records }
    fun setDailyAssets(dailyAssets: List<DailyAsset>) { _dailyAssets.value = dailyAssets }
    fun setBenchmarks(benchmarks: List<Benchmark>) { _benchmarks.value = benchmarks }

    override fun getRecordTypes(): Flow<List<CatRecordType>> = _recordTypes
    override fun getRecords(): Flow<List<CatRecord>> = _records

    override suspend fun addRecord(record: CatRecord): String {
        addRecordError?.let { throw it }
        val id = "fake-id-${_records.value.size}"
        _records.value = _records.value + record.copy(firestoreId = id)
        return id
    }

    var lastUpdatedRecord: CatRecord? = null

    override suspend fun updateRecord(record: CatRecord) {
        updateRecordError?.let { throw it }
        lastUpdatedRecord = record
    }

    override suspend fun deleteRecord(firestoreId: String) {
        deleteRecordError?.let { throw it }
        _records.value = _records.value.filter { it.firestoreId != firestoreId }
    }

    override suspend fun addRecordType(type: CatRecordType) {
        addRecordTypeError?.let { throw it }
        _recordTypes.value = _recordTypes.value + type
    }

    override suspend fun updateRecordType(type: CatRecordType) {
        updateRecordTypeError?.let { throw it }
    }

    override suspend fun deleteRecordTypeAndReassignRecords(
        typeDocId: String,
        affectedRecordIds: List<String>,
        fallbackTypeId: String,
    ) {
        deleteRecordTypeError?.let { throw it }
        _records.value = _records.value.map { record ->
            if (record.firestoreId in affectedRecordIds) record.copy(recordType = fallbackTypeId)
            else record
        }
        _recordTypes.value = _recordTypes.value.filter { it.docId != typeDocId }
    }

    override fun getDailyAssets(): Flow<List<DailyAsset>> = _dailyAssets

    override suspend fun upsertDailyAsset(asset: DailyAsset) {
        upsertDailyAssetError?.let { throw it }
        onUpsertDailyAsset?.invoke()
        val existingIndex = _dailyAssets.value.indexOfFirst { it.date == asset.date }
        _dailyAssets.value = if (existingIndex >= 0) {
            _dailyAssets.value.mapIndexed { index, existing -> if (index == existingIndex) asset else existing }
        } else {
            _dailyAssets.value + asset
        }
    }

    override suspend fun deleteDailyAsset(date: String) {
        deleteDailyAssetError?.let { throw it }
        _dailyAssets.value = _dailyAssets.value.filter { it.date != date }
    }

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
