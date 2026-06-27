package com.jkapp.data.firestore

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jkapp.data.model.CatRecord
import com.jkapp.data.model.CatRecordType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirestoreRepositoryImpl : FirestoreRepository {

    private val db = FirebaseFirestore.getInstance()
    private val recordTypesRef = db.collection("cat-record-types")
    private val recordsRef = db.collection("cat-records")

    override fun getRecordTypes(): Flow<List<CatRecordType>> = callbackFlow {
        val listener = recordTypesRef.addSnapshotListener { snapshot, error ->
            if (error != null) { close(error); return@addSnapshotListener }
            val types = snapshot?.documents?.mapNotNull { doc ->
                val id = doc.getString("id") ?: return@mapNotNull null
                CatRecordType(
                    id = id,
                    name = doc.getString("name") ?: "",
                    emoji = doc.getString("emoji") ?: "",
                    fontColor = doc.getString("font_color") ?: "#000000",
                    backgroundColor = doc.getString("background_color") ?: "#FFFFFF",
                )
            } ?: emptyList()
            trySend(types)
        }
        awaitClose { listener.remove() }
    }

    override fun getRecords(): Flow<List<CatRecord>> = callbackFlow {
        val listener = recordsRef
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val records = snapshot?.documents?.mapNotNull { doc ->
                    CatRecord(
                        firestoreId = doc.id,
                        date = doc.getString("date") ?: return@mapNotNull null,
                        recordType = doc.getString("record_type") ?: "",
                        record = doc.getString("record") ?: "",
                    )
                } ?: emptyList()
                trySend(records)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun addRecord(record: CatRecord): Unit = suspendCancellableCoroutine { cont ->
        recordsRef.add(record.toMap())
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    override suspend fun updateRecord(record: CatRecord): Unit = suspendCancellableCoroutine { cont ->
        val id = record.firestoreId ?: run { cont.resume(Unit); return@suspendCancellableCoroutine }
        recordsRef.document(id).update(record.toMap())
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    override suspend fun deleteRecord(firestoreId: String): Unit = suspendCancellableCoroutine { cont ->
        recordsRef.document(firestoreId).delete()
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    private fun CatRecord.toMap() = mapOf(
        "date" to date,
        "record_type" to recordType,
        "record" to record,
    )
}
