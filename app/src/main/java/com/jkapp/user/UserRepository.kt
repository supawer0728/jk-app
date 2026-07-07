package com.jkapp.user

import kotlinx.coroutines.flow.Flow

interface UserRepository {
    suspend fun upsertUserProfile(uid: String, email: String, displayName: String)
    fun observePreference(uid: String): Flow<UserPreference>
    suspend fun updatePreference(uid: String, preference: UserPreference)
}
