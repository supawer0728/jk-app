package com.jkapp.data.firestore

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.jkapp.data.model.Attachment
import com.jkapp.data.model.AssetItem
import com.jkapp.data.model.Benchmark
import com.jkapp.data.model.CatRecord
import com.jkapp.data.model.CatRecordType
import com.jkapp.data.model.DailyAsset
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
    private val dailyAssetsRef = db.collection(COLLECTION_DAILY_ASSETS)
    private val benchmarksRef = db.collection(COLLECTION_BENCHMARKS)

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

                trySend(records)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun addRecord(record: CatRecord): String = suspendCancellableCoroutine { cont ->
        recordsRef.add(record.toMap())
            .addOnSuccessListener { docRef -> cont.resume(docRef.id) }
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

    override suspend fun deleteRecordTypeAndReassignRecords(
        typeDocId: String,
        affectedRecordIds: List<String>,
        fallbackTypeId: String,
    ): Unit = suspendCancellableCoroutine { cont ->
        val batch = db.batch()
        affectedRecordIds.forEach { recordId ->
            batch.update(recordsRef.document(recordId), FIELD_RECORD_TYPE, fallbackTypeId)
        }
        batch.delete(recordTypesRef.document(typeDocId))
        batch.commit()
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    override fun getDailyAssets(): Flow<List<DailyAsset>> = callbackFlow {
        val listener = dailyAssetsRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val dailyAssets = snapshot?.documents?.map { doc ->
                @Suppress("UNCHECKED_CAST")
                val assets = (doc.get(FIELD_ASSETS) as? List<Map<String, Any?>>)
                    ?.mapNotNull { it.toAssetItem() } ?: emptyList()
                DailyAsset(
                    firestoreId = doc.id,
                    date = doc.getString(FIELD_DATE) ?: doc.id,
                    assets = assets,
                )
            }?.sortedByDescending { it.date } ?: emptyList()

            trySend(dailyAssets)
        }
        awaitClose { listener.remove() }
    }

    override suspend fun upsertDailyAsset(asset: DailyAsset): Unit = suspendCancellableCoroutine { cont ->
        dailyAssetsRef.document(asset.date).set(asset.toMap())
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    override suspend fun deleteDailyAsset(date: String): Unit = suspendCancellableCoroutine { cont ->
        dailyAssetsRef.document(date).delete()
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    override fun getBenchmarks(): Flow<List<Benchmark>> = callbackFlow {
        val listener = benchmarksRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val benchmarks = snapshot?.documents
                ?.mapNotNull { it.toBenchmark() }
                ?.sortedByDescending { it.date } ?: emptyList()

            trySend(benchmarks)
        }
        awaitClose { listener.remove() }
    }

    override suspend fun upsertBenchmark(benchmark: Benchmark): Unit = suspendCancellableCoroutine { cont ->
        benchmarksRef.document(benchmark.date).set(benchmark.toMap())
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    override suspend fun deleteBenchmark(date: String): Unit = suspendCancellableCoroutine { cont ->
        benchmarksRef.document(date).delete()
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    private fun Benchmark.toMap() = mapOf(
        FIELD_DATE to date,
        FIELD_ADDITIONAL_INVESTMENT to additionalInvestment.toPlainString(),
        FIELD_PRINCIPAL to principal.toPlainString(),
        FIELD_CURRENT_AMOUNT to currentAmount.toPlainString(),
        FIELD_KOSPI to kospi.toPlainString(),
        FIELD_SNP500 to snp500.toPlainString(),
        FIELD_NASDAQ to nasdaq.toPlainString(),
    )

    // 숫자 필드가 하나라도 없거나 파싱에 실패하면 표에 잘못된 값을 보여주는 대신 건너뛰고 로그를 남긴다.
    private fun DocumentSnapshot.toBenchmark(): Benchmark? {
        val additionalInvestment = getString(FIELD_ADDITIONAL_INVESTMENT)?.toBigDecimalOrNull()
        val principal = getString(FIELD_PRINCIPAL)?.toBigDecimalOrNull()
        val currentAmount = getString(FIELD_CURRENT_AMOUNT)?.toBigDecimalOrNull()
        val kospi = getString(FIELD_KOSPI)?.toBigDecimalOrNull()
        val snp500 = getString(FIELD_SNP500)?.toBigDecimalOrNull()
        val nasdaq = getString(FIELD_NASDAQ)?.toBigDecimalOrNull()
        if (additionalInvestment == null || principal == null || currentAmount == null ||
            kospi == null || snp500 == null || nasdaq == null
        ) {
            Log.w(TAG, "benchmarks 문서에 숫자 필드가 누락되어 건너뜁니다: id=$id")
            return null
        }
        return Benchmark(
            firestoreId = id,
            date = getString(FIELD_DATE) ?: id,
            additionalInvestment = additionalInvestment,
            principal = principal,
            currentAmount = currentAmount,
            kospi = kospi,
            snp500 = snp500,
            nasdaq = nasdaq,
        )
    }

    private fun DailyAsset.toMap() = mapOf(
        FIELD_DATE to date,
        FIELD_ASSETS to assets.map { it.toMap() },
    )

    private fun AssetItem.toMap(): Map<String, Any?> = mapOf(
        FIELD_ASSET_NAME to name,
        FIELD_ASSET_OWNER to owner,
        FIELD_ASSET_INSTITUTION to institution,
        FIELD_ASSET_ACCOUNT_NUMBER to accountNumber,
        FIELD_ASSET_CARD to card,
        FIELD_ASSET_AMOUNT to amount?.toPlainString(),
        FIELD_ASSET_HIDDEN to hidden,
    )

    // name/owner가 없는 항목은 걸러지는데, 이 문서를 이후 add/update/delete로 다시 저장하면
    // 걸러진 항목이 전체 upsert(set)로 인해 영구히 사라지므로 최소한 로그로 남긴다.
    private fun Map<String, Any?>.toAssetItem(): AssetItem? {
        val name = this[FIELD_ASSET_NAME] as? String
        val owner = this[FIELD_ASSET_OWNER] as? String
        if (name == null || owner == null) {
            Log.w(TAG, "daily-assets 문서에 name/owner가 없는 자산 항목이 있어 건너뜁니다: $this")
            return null
        }
        return AssetItem(
            name = name,
            owner = owner,
            institution = this[FIELD_ASSET_INSTITUTION] as? String,
            accountNumber = this[FIELD_ASSET_ACCOUNT_NUMBER] as? String,
            card = this[FIELD_ASSET_CARD] as? String,
            amount = (this[FIELD_ASSET_AMOUNT] as? String)?.toBigDecimalOrNull(),
            hidden = this[FIELD_ASSET_HIDDEN] as? Boolean ?: false,
        )
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
        private const val TAG = "FirestoreRepositoryImpl"

        // Collections
        private const val COLLECTION_RECORD_TYPES = "cat-record-types"
        private const val COLLECTION_RECORDS = "cat-records"
        private const val COLLECTION_DAILY_ASSETS = "daily-assets"
        private const val COLLECTION_BENCHMARKS = "benchmarks"

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
        private const val FIELD_ATTACHMENTS = "attachments"

        // Field names - Attachment
        private const val FIELD_ATTACHMENT_FILE_ID = "fileId"
        private const val FIELD_ATTACHMENT_NAME = "name"
        private const val FIELD_ATTACHMENT_MIME_TYPE = "mimeType"
        private const val FIELD_ATTACHMENT_SIZE = "size"

        // Field names - DailyAsset / AssetItem
        private const val FIELD_ASSETS = "assets"
        private const val FIELD_ASSET_NAME = "name"
        private const val FIELD_ASSET_OWNER = "owner"
        private const val FIELD_ASSET_INSTITUTION = "institution"
        private const val FIELD_ASSET_ACCOUNT_NUMBER = "accountNumber"
        private const val FIELD_ASSET_CARD = "card"
        private const val FIELD_ASSET_AMOUNT = "amount"
        private const val FIELD_ASSET_HIDDEN = "hidden"

        // Field names - Benchmark
        private const val FIELD_ADDITIONAL_INVESTMENT = "additionalInvestment"
        private const val FIELD_PRINCIPAL = "principal"
        private const val FIELD_CURRENT_AMOUNT = "currentAmount"
        private const val FIELD_KOSPI = "kospi"
        private const val FIELD_SNP500 = "snp500"
        private const val FIELD_NASDAQ = "nasdaq"
    }
}
