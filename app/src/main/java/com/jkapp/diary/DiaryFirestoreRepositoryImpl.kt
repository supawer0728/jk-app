package com.jkapp.diary

import com.jkapp.common.AppFirestore
import com.jkapp.common.await
import com.jkapp.common.snapshotFlow
import com.jkapp.drive.Attachment
import kotlinx.coroutines.flow.Flow

class DiaryFirestoreRepositoryImpl : DiaryFirestoreRepository {

    private val db = AppFirestore.instance
    private val recordTypesRef = db.collection(COLLECTION_RECORD_TYPES)
    private val recordsRef = db.collection(COLLECTION_RECORDS)

    override fun getRecordTypes(): Flow<List<CatRecordType>> = recordTypesRef.snapshotFlow { snapshot ->
        snapshot?.documents?.map { doc ->
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
    }

    override fun getRecords(): Flow<List<CatRecord>> = recordsRef.snapshotFlow { snapshot ->
        snapshot?.documents?.mapNotNull { doc ->
            @Suppress("UNCHECKED_CAST")
            val attachments = (doc.get(FIELD_ATTACHMENTS) as? List<Map<String, Any>>)
                ?.map { map ->
                    Attachment(
                        fileId = map[FIELD_ATTACHMENT_FILE_ID] as? String ?: "",
                        name = map[FIELD_ATTACHMENT_NAME] as? String ?: "",
                        mimeType = map[FIELD_ATTACHMENT_MIME_TYPE] as? String ?: "",
                        size = (map[FIELD_ATTACHMENT_SIZE] as? Long) ?: 0L,
                    )
                } ?: emptyList()
            CatRecord(
                firestoreId = doc.id,
                date = doc.getString(FIELD_DATE) ?: return@mapNotNull null,
                recordType = doc.getString(FIELD_RECORD_TYPE) ?: "",
                record = doc.getString(FIELD_RECORD) ?: "",
                attachments = attachments,
            )
        } ?: emptyList()
    }

    override suspend fun addRecord(record: CatRecord): String =
        recordsRef.add(record.toMap()).await().id

    override suspend fun updateRecord(record: CatRecord) {
        val id = record.firestoreId ?: throw IllegalArgumentException("수정할 기록의 ID가 없습니다")
        recordsRef.document(id).update(record.toMap()).await()
    }

    override suspend fun deleteRecord(firestoreId: String) {
        recordsRef.document(firestoreId).delete().await()
    }

    override suspend fun addRecordType(type: CatRecordType) {
        val ref = if (type.id.isNotBlank()) recordTypesRef.document(type.id) else recordTypesRef.document()
        ref.set(type.toMap()).await()
    }

    override suspend fun updateRecordType(type: CatRecordType) {
        val docId = type.docId.ifBlank { type.id }
        recordTypesRef.document(docId).set(type.toMap()).await()
    }

    override suspend fun deleteRecordTypeAndReassignRecords(
        typeDocId: String,
        affectedRecordIds: List<String>,
        fallbackTypeId: String,
    ) {
        val batch = db.batch()
        affectedRecordIds.forEach { recordId ->
            batch.update(recordsRef.document(recordId), FIELD_RECORD_TYPE, fallbackTypeId)
        }
        batch.delete(recordTypesRef.document(typeDocId))
        batch.commit().await()
    }

    private fun CatRecord.toMap() = mapOf(
        FIELD_DATE to date,
        FIELD_RECORD_TYPE to recordType,
        FIELD_RECORD to record,
        FIELD_ATTACHMENTS to attachments.map { it.toMap() },
    )

    private fun Attachment.toMap() = mapOf(
        FIELD_ATTACHMENT_FILE_ID to fileId,
        FIELD_ATTACHMENT_NAME to name,
        FIELD_ATTACHMENT_MIME_TYPE to mimeType,
        FIELD_ATTACHMENT_SIZE to size,
    )

    private fun CatRecordType.toMap() = mapOf(
        FIELD_ID to id,
        FIELD_NAME to name,
        FIELD_EMOJI to emoji,
        FIELD_FONT_COLOR_CAMEL to fontColor,
        FIELD_BG_COLOR_CAMEL to backgroundColor,
    )

    companion object {
        private const val COLLECTION_RECORD_TYPES = "cat-record-types"
        private const val COLLECTION_RECORDS = "cat-records"

        private const val FIELD_ID = "id"
        private const val FIELD_NAME = "name"
        private const val FIELD_EMOJI = "emoji"
        private const val FIELD_FONT_COLOR_CAMEL = "fontColor"
        private const val FIELD_FONT_COLOR_SNAKE = "font_color"
        private const val FIELD_BG_COLOR_CAMEL = "backgroundColor"
        private const val FIELD_BG_COLOR_SNAKE = "background_color"

        private const val FIELD_DATE = "date"
        private const val FIELD_RECORD_TYPE = "record_type"
        private const val FIELD_RECORD = "record"
        private const val FIELD_ATTACHMENTS = "attachments"

        private const val FIELD_ATTACHMENT_FILE_ID = "fileId"
        private const val FIELD_ATTACHMENT_NAME = "name"
        private const val FIELD_ATTACHMENT_MIME_TYPE = "mimeType"
        private const val FIELD_ATTACHMENT_SIZE = "size"
    }
}
