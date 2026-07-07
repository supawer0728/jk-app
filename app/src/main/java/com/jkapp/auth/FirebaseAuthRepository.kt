package com.jkapp.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FirebaseAuthRepository : AuthRepository {
    override fun getCurrentUserEmail(): String? =
        FirebaseAuth.getInstance().currentUser?.email

    override fun observeAuthState(): Flow<Boolean> = authStateFlow { it != null }

    override fun observeCurrentUserEmail(): Flow<String?> = authStateFlow { it?.email }

    override fun observeCurrentUserId(): Flow<String?> = authStateFlow { it?.uid }

    private fun <T> authStateFlow(map: (FirebaseUser?) -> T): Flow<T> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth -> trySend(map(auth.currentUser)) }
        FirebaseAuth.getInstance().addAuthStateListener(listener)
        awaitClose { FirebaseAuth.getInstance().removeAuthStateListener(listener) }
    }
}
