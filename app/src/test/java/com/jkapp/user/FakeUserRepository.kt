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

    // uid <-> email 매핑과 getPushTokensByEmails 응답을 테스트가 직접 세팅할 수 있게 한다.
    private val emailsByUid = mutableMapOf<String, String>()
    var getPushTokensByEmailsError: Throwable? = null
    var lastRequestedEmails: List<String>? = null

    fun setUserEmail(uid: String, email: String) {
        emailsByUid[uid] = email
    }

    override suspend fun getPushTokensByEmails(emails: List<String>): List<UserPushTarget> {
        lastRequestedEmails = emails
        getPushTokensByEmailsError?.let { throw it }
        if (emails.isEmpty()) return emptyList()
        return emailsByUid.entries
            .filter { (_, email) -> email in emails }
            .mapNotNull { (uid, _) -> pushTokensByUid[uid]?.let { UserPushTarget(uid, it.token) } }
    }
}
