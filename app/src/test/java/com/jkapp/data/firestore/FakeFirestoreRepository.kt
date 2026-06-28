package com.jkapp.data.firestore

import com.jkapp.data.model.CatRecord
import com.jkapp.data.model.CatRecordType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeFirestoreRepository : FirestoreRepository {

    private val _recordTypes = MutableStateFlow<List<CatRecordType>>(emptyList())
    private val _records = MutableStateFlow<List<CatRecord>>(emptyList())

    var addRecordError: Throwable? = null
    var updateRecordError: Throwable? = null
    var deleteRecordError: Throwable? = null
    var addRecordTypeError: Throwable? = null
    var updateRecordTypeError: Throwable? = null
    var deleteRecordTypeError: Throwable? = null

    fun setRecordTypes(types: List<CatRecordType>) { _recordTypes.value = types }
    fun setRecords(records: List<CatRecord>) { _records.value = records }

    override fun getRecordTypes(): Flow<List<CatRecordType>> = _recordTypes
    override fun getRecords(): Flow<List<CatRecord>> = _records

    override suspend fun addRecord(record: CatRecord) {
        addRecordError?.let { throw it }
        _records.value = _records.value + record
    }

    override suspend fun updateRecord(record: CatRecord) {
        updateRecordError?.let { throw it }
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
}
