package com.jkapp.user

class FakeUserRepository : UserRepository {

    var upsertUserProfileError: Throwable? = null
    var lastUpsertedUid: String? = null
    var lastUpsertedEmail: String? = null
    var lastUpsertedDisplayName: String? = null
    var upsertCallCount = 0

    override suspend fun upsertUserProfile(uid: String, email: String, displayName: String) {
        upsertCallCount++
        upsertUserProfileError?.let { throw it }
        lastUpsertedUid = uid
        lastUpsertedEmail = email
        lastUpsertedDisplayName = displayName
    }
}
