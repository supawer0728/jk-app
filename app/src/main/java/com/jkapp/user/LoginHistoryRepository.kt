package com.jkapp.user

interface LoginHistoryRepository {
    suspend fun recordLogin(uid: String, device: LoginDevice)
}
