package com.jkapp.auth

import android.app.Application
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.ClearCredentialException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AuthViewModel(
    app: Application,
    private val auth: FirebaseAuth,
    private val credentialManager: CredentialManager,
) : AndroidViewModel(app) {

    constructor(app: Application) : this(
        app = app,
        auth = FirebaseAuth.getInstance(),
        credentialManager = CredentialManager.create(app),
    )

    private val _user = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val user: StateFlow<FirebaseUser?> = _user.asStateFlow()

    fun firebaseAuthWithGoogle(idToken: String, onResult: (Boolean) -> Unit) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        viewModelScope.launch {
            runCatching {
                suspendCancellableCoroutine { cont ->
                    auth.signInWithCredential(credential)
                        .addOnSuccessListener { authResult ->
                            val user = authResult.user
                            if (user == null) {
                                cont.resumeWithException(IllegalStateException("authResult.user is null after successful sign-in"))
                                return@addOnSuccessListener
                            }
                            _user.value = user
                            cont.resume(Unit)
                        }
                        .addOnFailureListener { cont.resumeWithException(it) }
                }
            }.onFailure { e ->
                if (e is CancellationException) throw e
            }.fold(
                onSuccess = { onResult(true) },
                onFailure = { onResult(false) },
            )
        }
    }

    fun signOut() {
        auth.signOut()
        _user.value = null
        viewModelScope.launch {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (_: ClearCredentialException) {
                // sign-out already completed; credential state clear is best-effort
            }
        }
    }
}
