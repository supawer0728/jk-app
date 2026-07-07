package com.jkapp.user

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeUserRepository : UserRepository {

    var upsertUserProfileError: Throwable? = null
    var lastUpsertedUid: String? = null
    var lastUpsertedEmail: String? = null
    var lastUpsertedDisplayName: String? = null
    var upsertCallCount = 0

    private val preferencesByUid = mutableMapOf<String, MutableStateFlow<UserPreference>>()
    var updatePreferenceError: Throwable? = null
    var lastUpdatedPreferenceUid: String? = null
    var lastUpdatedPreference: UserPreference? = null

    private val pushTokensByUid = mutableMapOf<String, PushToken>()
    var getPushTokenError: Throwable? = null
    var updatePushTokenError: Throwable? = null
    var lastUpdatedPushTokenUid: String? = null
    var lastUpdatedPushToken: PushToken? = null
    var updatePushTokenCallCount = 0

    override suspend fun upsertUserProfile(uid: String, email: String, displayName: String) {
        upsertCallCount++
        upsertUserProfileError?.let { throw it }
        lastUpsertedUid = uid
        lastUpsertedEmail = email
        lastUpsertedDisplayName = displayName
    }

    override fun observePreference(uid: String): Flow<UserPreference> =
        preferencesByUid.getOrPut(uid) { MutableStateFlow(UserPreference()) }

    override suspend fun updatePreference(uid: String, preference: UserPreference) {
        lastUpdatedPreferenceUid = uid
        lastUpdatedPreference = preference
        updatePreferenceError?.let { throw it }
        preferencesByUid.getOrPut(uid) { MutableStateFlow(UserPreference()) }.value = preference
    }

    override suspend fun getPushToken(uid: String): PushToken? {
        getPushTokenError?.let { throw it }
        return pushTokensByUid[uid]
    }

    override suspend fun updatePushToken(uid: String, pushToken: PushToken) {
        updatePushTokenCallCount++
        lastUpdatedPushTokenUid = uid
        lastUpdatedPushToken = pushToken
        updatePushTokenError?.let { throw it }
        pushTokensByUid[uid] = pushToken
    }
}
