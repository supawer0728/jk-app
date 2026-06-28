package com.jkapp.auth

import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun observeAuthState(): Flow<Boolean>
    fun observeCurrentUserEmail(): Flow<String?>
    fun getCurrentUserEmail(): String?
}
