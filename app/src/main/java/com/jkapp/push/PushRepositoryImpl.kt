package com.jkapp.push

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.jkapp.common.AppFirestore
import com.jkapp.common.await
import java.time.Instant

class PushRepositoryImpl(
    private val db: FirebaseFirestore = AppFirestore.instance,
) : PushRepository {

    private val pushesRef = db.collection(COLLECTION_PUSHES)

    override suspend fun createPush(message: PushMessage): String {
        val data = mapOf(
            FIELD_TITLE to message.title,
            FIELD_BODY to message.body,
            FIELD_CHANNEL_ID to message.channelId,
            FIELD_TOKENS to message.tokens,
            FIELD_STATUS to STATUS_PENDING,
            FIELD_CREATED_AT to Timestamp.now(),
        )
        return pushesRef.add(data).await().id
    }

    // Firestore write batch는 최대 500 연산이므로, 만료 문서를 500 단위로 끊어 조회·삭제를
    // 반복한다. 매 회 오래된 순으로 500개를 지우고, 더 이상 남지 않을 때까지 돈다. 하루 1회
    // 실행되므로 정상 상황에서는 대개 1회로 끝나지만, 장기 미실행 후 대량 누적을 안전하게 처리한다.
    override suspend fun deleteExpiredPushes(threshold: Instant): Int {
        val thresholdTs = Timestamp(threshold.epochSecond, threshold.nano)
        var deleted = 0
        // 마지막 페이지(500 미만)를 지우면 더 남은 문서가 없으므로 반복을 끝낸다. 단일 종료 조건으로
        // 표현하려고 매 회 삭제 건수(pageSize)를 조건으로 검사한다.
        var pageSize = DELETE_BATCH_LIMIT.toInt()
        while (pageSize >= DELETE_BATCH_LIMIT) {
            val expired = pushesRef
                .whereLessThan(FIELD_CREATED_AT, thresholdTs)
                .orderBy(FIELD_CREATED_AT)
                .limit(DELETE_BATCH_LIMIT)
                .get()
                .await()
            pageSize = expired.size()
            if (pageSize > 0) {
                val batch = db.batch()
                expired.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
                deleted += pageSize
            }
        }
        return deleted
    }

    companion object {
        private const val COLLECTION_PUSHES = "pushes"

        private const val FIELD_TITLE = "title"
        private const val FIELD_BODY = "body"
        private const val FIELD_CHANNEL_ID = "channelId"
        private const val FIELD_TOKENS = "tokens"
        private const val FIELD_STATUS = "status"
        private const val FIELD_CREATED_AT = "createdAt"

        private const val STATUS_PENDING = "pending"

        // Firestore write batch 최대 연산 수.
        private const val DELETE_BATCH_LIMIT = 500L
    }
}
