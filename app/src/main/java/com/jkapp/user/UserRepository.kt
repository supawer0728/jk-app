package com.jkapp.user

interface UserRepository {
    suspend fun upsertUserProfile(uid: String, email: String, displayName: String)
}
