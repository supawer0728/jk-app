package com.jkapp.finance.benchmark

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine

class BenchmarkFirestoreRepositoryImpl : BenchmarkFirestoreRepository {

    private val db = FirebaseFirestore.getInstance()
    private val benchmarksRef = db.collection(COLLECTION_BENCHMARKS)

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

    // 여러 날짜를 개별 set()으로 순차 호출하는 대신 하나의 배치로 묶어 한 번에 커밋한다.
    // Firestore 배치는 원자적이라 일부만 저장되는 상태 없이 전체 성공/실패로 귀결된다.
    override suspend fun upsertBenchmarks(benchmarks: List<Benchmark>): Unit = suspendCancellableCoroutine { cont ->
        val batch = db.batch()
        benchmarks.forEach { benchmark -> batch.set(benchmarksRef.document(benchmark.date), benchmark.toMap()) }
        batch.commit()
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    override suspend fun deleteBenchmark(date: String): Unit = suspendCancellableCoroutine { cont ->
        benchmarksRef.document(date).delete()
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    // 여러 날짜를 개별 delete()로 순차 호출하는 대신 하나의 배치로 묶어 한 번에 커밋한다.
    // Firestore 배치는 원자적이라 일부만 삭제되는 상태 없이 전체 성공/실패로 귀결된다.
    override suspend fun deleteBenchmarks(dates: List<String>): Unit = suspendCancellableCoroutine { cont ->
        val batch = db.batch()
        dates.forEach { date -> batch.delete(benchmarksRef.document(date)) }
        batch.commit()
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    // getBenchmarks()의 필터링된 목록이 아니라 컬렉션을 직접 조회해 삭제 대상을 정하므로,
    // 역직렬화에 실패한 손상된 문서도 함께 삭제된다.
    override suspend fun deleteAllBenchmarks(): Unit = suspendCancellableCoroutine { cont ->
        benchmarksRef.get()
            .addOnSuccessListener { snapshot ->
                val batch = db.batch()
                snapshot.documents.forEach { doc -> batch.delete(doc.reference) }
                batch.commit()
                    .addOnSuccessListener { cont.resume(Unit) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    private fun Benchmark.toMap() = mapOf(
        FIELD_DATE to date,
        FIELD_ADDITIONAL_INVESTMENT to additionalInvestment.toPlainString(),
        FIELD_CURRENT_AMOUNT to currentAmount.toPlainString(),
        FIELD_KOSPI to kospi.toPlainString(),
        FIELD_SNP500 to snp500.toPlainString(),
        FIELD_NASDAQ to nasdaq.toPlainString(),
    )

    // 숫자 필드가 하나라도 없거나 파싱에 실패하면 표에 잘못된 값을 보여주는 대신 건너뛰고 로그를 남긴다.
    private fun DocumentSnapshot.toBenchmark(): Benchmark? {
        val additionalInvestment = getString(FIELD_ADDITIONAL_INVESTMENT)?.toBigDecimalOrNull()
        val currentAmount = getString(FIELD_CURRENT_AMOUNT)?.toBigDecimalOrNull()
        val kospi = getString(FIELD_KOSPI)?.toBigDecimalOrNull()
        val snp500 = getString(FIELD_SNP500)?.toBigDecimalOrNull()
        val nasdaq = getString(FIELD_NASDAQ)?.toBigDecimalOrNull()
        if (additionalInvestment == null || currentAmount == null ||
            kospi == null || snp500 == null || nasdaq == null
        ) {
            Log.w(TAG, "benchmarks 문서에 숫자 필드가 누락되어 건너뜁니다: id=$id")
            return null
        }
        return Benchmark(
            firestoreId = id,
            date = getString(FIELD_DATE) ?: id,
            additionalInvestment = additionalInvestment,
            currentAmount = currentAmount,
            kospi = kospi,
            snp500 = snp500,
            nasdaq = nasdaq,
        )
    }

    companion object {
        private const val TAG = "BenchmarkFirestoreRepositoryImpl"

        private const val COLLECTION_BENCHMARKS = "benchmarks"

        private const val FIELD_DATE = "date"
        private const val FIELD_ADDITIONAL_INVESTMENT = "additionalInvestment"
        private const val FIELD_CURRENT_AMOUNT = "currentAmount"
        private const val FIELD_KOSPI = "kospi"
        private const val FIELD_SNP500 = "snp500"
        private const val FIELD_NASDAQ = "nasdaq"
    }
}
