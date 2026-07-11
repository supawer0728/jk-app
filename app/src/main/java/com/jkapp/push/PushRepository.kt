package com.jkapp.push

import java.time.Instant

interface PushRepository {
    // pushes 문서를 status="pending"으로 생성한다. Cloud Functions의 on_document_created 트리거가
    // 이 문서를 발송하고 status/sentAt/results를 기록한다(→ doc/dev/infra/push.md, functions.md).
    // 생성된 문서 ID를 반환한다.
    suspend fun createPush(message: PushMessage): String

    // createdAt이 threshold보다 오래된 pushes 문서를 status와 무관하게 삭제하고 삭제 건수를
    // 반환한다. PushCleanupScheduler가 하루 1회 가드를 거쳐 호출한다.
    suspend fun deleteExpiredPushes(threshold: Instant): Int
}
