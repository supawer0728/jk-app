package com.jkapp.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeAuthRepository(initialLoggedIn: Boolean = false) : AuthRepository {
    private val _authState = MutableStateFlow(initialLoggedIn)

    var currentEmail: String? = null

    fun setLoggedIn(isLoggedIn: Boolean) {
        _authState.value = isLoggedIn
    }

    override fun getCurrentUserEmail(): String? = currentEmail
    override fun observeAuthState(): Flow<Boolean> = _authState
    override fun observeCurrentUserEmail(): Flow<String?> = _authState.map { if (it) currentEmail else null }
}
