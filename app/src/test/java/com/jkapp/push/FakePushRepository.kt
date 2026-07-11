package com.jkapp.push

import java.time.Instant

class FakePushRepository : PushRepository {

    var createPushError: Throwable? = null
    val createdMessages = mutableListOf<PushMessage>()

    var deleteExpiredPushesError: Throwable? = null
    var deleteExpiredPushesCallCount = 0
    var lastDeleteThreshold: Instant? = null
    var deleteExpiredPushesResult = 0

    override suspend fun createPush(message: PushMessage): String {
        createPushError?.let { throw it }
        createdMessages += message
        return "fake-push-${createdMessages.size}"
    }

    override suspend fun deleteExpiredPushes(threshold: Instant): Int {
        deleteExpiredPushesCallCount++
        lastDeleteThreshold = threshold
        deleteExpiredPushesError?.let { throw it }
        return deleteExpiredPushesResult
    }
}
