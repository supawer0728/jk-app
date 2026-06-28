package com.jkapp.auth

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FirebaseAuthRepository : AuthRepository {
    override fun observeAuthState(): Flow<Boolean> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser != null)
        }
        FirebaseAuth.getInstance().addAuthStateListener(listener)
        awaitClose { FirebaseAuth.getInstance().removeAuthStateListener(listener) }
    }
}
