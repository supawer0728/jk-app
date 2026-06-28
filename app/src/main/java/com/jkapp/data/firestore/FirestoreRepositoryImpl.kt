package com.jkapp.data.firestore

import com.google.firebase.firestore.FirebaseFirestore
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
    private val recordTypesRef = db.collection(COLLECTION_RECORD_TYPES)
    private val recordsRef = db.collection(COLLECTION_RECORDS)

    override fun getRecordTypes(): Flow<List<CatRecordType>> = callbackFlow {
        val listener = recordTypesRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val types = snapshot?.documents?.map { doc ->
                val id = doc.getString(FIELD_ID) ?: doc.id
                CatRecordType(
                    id = id,
                    name = doc.getString(FIELD_NAME) ?: id,
                    emoji = doc.getString(FIELD_EMOJI) ?: "📝",
                    fontColor = doc.getString(FIELD_FONT_COLOR_CAMEL) ?: doc.getString(FIELD_FONT_COLOR_SNAKE) ?: "#000000",
                    backgroundColor = doc.getString(FIELD_BG_COLOR_CAMEL) ?: doc.getString(FIELD_BG_COLOR_SNAKE) ?: "#FFFFFF",
                    docId = doc.id,
                )
            }?.sortedBy { it.name } ?: emptyList()

            trySend(types)
        }
        awaitClose { listener.remove() }
    }

    override fun getRecords(): Flow<List<CatRecord>> = callbackFlow {
        val listener = recordsRef
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val records = snapshot?.documents?.mapNotNull { doc ->
                    CatRecord(
                        firestoreId = doc.id,
                        date = doc.getString(FIELD_DATE) ?: return@mapNotNull null,
                        recordType = doc.getString(FIELD_RECORD_TYPE) ?: "",
                        record = doc.getString(FIELD_RECORD) ?: "",
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
        val id = record.firestoreId ?: run {
            cont.resumeWithException(IllegalArgumentException("수정할 기록의 ID가 없습니다"))
            return@suspendCancellableCoroutine
        }
        recordsRef.document(id).update(record.toMap())
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    override suspend fun deleteRecord(firestoreId: String): Unit = suspendCancellableCoroutine { cont ->
        recordsRef.document(firestoreId).delete()
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    override suspend fun addRecordType(type: CatRecordType): Unit = suspendCancellableCoroutine { cont ->
        val ref = if (type.id.isNotBlank()) recordTypesRef.document(type.id) else recordTypesRef.document()
        ref.set(type.toMap())
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    override suspend fun updateRecordType(type: CatRecordType): Unit = suspendCancellableCoroutine { cont ->
        val docId = type.docId.ifBlank { type.id }
        recordTypesRef.document(docId).set(type.toMap())
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    override suspend fun deleteRecordType(docId: String): Unit = suspendCancellableCoroutine { cont ->
        recordTypesRef.document(docId).delete()
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    private fun CatRecord.toMap() = mapOf(
        FIELD_DATE to date,
        FIELD_RECORD_TYPE to recordType,
        FIELD_RECORD to record,
    )

    private fun CatRecordType.toMap() = mapOf(
        FIELD_ID to id,
        FIELD_NAME to name,
        FIELD_EMOJI to emoji,
        FIELD_FONT_COLOR_CAMEL to fontColor,
        FIELD_BG_COLOR_CAMEL to backgroundColor,
    )

    companion object {
        // Collections
        private const val COLLECTION_RECORD_TYPES = "cat-record-types"
        private const val COLLECTION_RECORDS = "cat-records"

        // Field names - CatRecordType
        private const val FIELD_ID = "id"
        private const val FIELD_NAME = "name"
        private const val FIELD_EMOJI = "emoji"
        private const val FIELD_FONT_COLOR_CAMEL = "fontColor"
        private const val FIELD_FONT_COLOR_SNAKE = "font_color"
        private const val FIELD_BG_COLOR_CAMEL = "backgroundColor"
        private const val FIELD_BG_COLOR_SNAKE = "background_color"

        // Field names - CatRecord
        private const val FIELD_DATE = "date"
        private const val FIELD_RECORD_TYPE = "record_type"
        private const val FIELD_RECORD = "record"
    }
}
