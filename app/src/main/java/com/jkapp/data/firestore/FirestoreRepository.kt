package com.jkapp.data.firestore

import com.jkapp.data.model.CatRecord
import com.jkapp.data.model.CatRecordType
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
}
