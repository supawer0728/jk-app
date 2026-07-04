package com.jkapp.data.firestore

import com.jkapp.data.model.CatRecord
import com.jkapp.data.model.CatRecordType
import com.jkapp.data.model.DailyAsset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeFirestoreRepository : FirestoreRepository {

    private val _recordTypes = MutableStateFlow<List<CatRecordType>>(emptyList())
    private val _records = MutableStateFlow<List<CatRecord>>(emptyList())
    private val _dailyAssets = MutableStateFlow<List<DailyAsset>>(emptyList())

    var addRecordError: Throwable? = null
    var updateRecordError: Throwable? = null
    var deleteRecordError: Throwable? = null
    var addRecordTypeError: Throwable? = null
    var updateRecordTypeError: Throwable? = null
    var deleteRecordTypeError: Throwable? = null
    var upsertDailyAssetError: Throwable? = null
    var deleteDailyAssetError: Throwable? = null

    fun setRecordTypes(types: List<CatRecordType>) { _recordTypes.value = types }
    fun setRecords(records: List<CatRecord>) { _records.value = records }
    fun setDailyAssets(dailyAssets: List<DailyAsset>) { _dailyAssets.value = dailyAssets }

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
}
