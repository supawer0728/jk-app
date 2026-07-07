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
}
