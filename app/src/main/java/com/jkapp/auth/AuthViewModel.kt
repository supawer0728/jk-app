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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val AUTH_READY_TIMEOUT_MS = 5_000L

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

    // auth.currentUser는 세션 복원 전에 잠깐 null을 반환할 수 있어, 인증 확인이
    // 실제로 끝났는지는 addAuthStateListener의 첫 호출 시점으로 판단한다.
    private val _isAuthReady = MutableStateFlow(false)
    val isAuthReady: StateFlow<Boolean> = _isAuthReady.asStateFlow()

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        _user.value = firebaseAuth.currentUser
        _isAuthReady.value = true
    }

    init {
        auth.addAuthStateListener(authStateListener)
        // authStateListener가 끝내 발화하지 않는 극단적 상황(Firebase 초기화 실패 등)에서도
        // 스플래시에 영구히 머무르지 않도록 타임아웃 후 강제로 준비 완료 처리한다.
        viewModelScope.launch {
            delay(AUTH_READY_TIMEOUT_MS)
            if (!_isAuthReady.value) {
                _isAuthReady.value = true
            }
        }
    }

    override fun onCleared() {
        auth.removeAuthStateListener(authStateListener)
        super.onCleared()
    }

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
