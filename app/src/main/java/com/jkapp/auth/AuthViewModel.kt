package com.jkapp.auth

import android.app.Application
import android.os.Build
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.ClearCredentialException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.jkapp.BuildConfig
import com.jkapp.user.LoginDevice
import com.jkapp.user.LoginHistoryRepository
import com.jkapp.user.LoginHistoryRepositoryImpl
import com.jkapp.user.UserRepository
import com.jkapp.user.UserRepositoryImpl
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
    private val userRepository: UserRepository = UserRepositoryImpl(),
    private val loginHistoryRepository: LoginHistoryRepository = LoginHistoryRepositoryImpl(),
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
        // TODO(임시 진단): 재기동 시 세션 복원 여부/타이밍 확인용. 원인 파악 후 제거.
        android.util.Log.w("AuthDebug", "authStateListener 발화: currentUser 존재=${firebaseAuth.currentUser != null}")
        _user.value = firebaseAuth.currentUser
        _isAuthReady.value = true
    }

    init {
        // TODO(임시 진단): 재기동 시 세션 복원 여부/타이밍 확인용. 원인 파악 후 제거.
        android.util.Log.w("AuthDebug", "AuthViewModel init: 생성 시점 currentUser 존재=${auth.currentUser != null}")
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
                val signedInUser = suspendCancellableCoroutine { cont ->
                    auth.signInWithCredential(credential)
                        .addOnSuccessListener { authResult ->
                            val user = authResult.user
                            if (user == null) {
                                cont.resumeWithException(IllegalStateException("authResult.user is null after successful sign-in"))
                                return@addOnSuccessListener
                            }
                            cont.resume(user)
                        }
                        .addOnFailureListener { cont.resumeWithException(it) }
                }
                // 프로필 upsert/로그인 기록도 같은 runCatching 안에서 처리해, 실패 시 로그인 자체도 실패로 취급한다.
                userRepository.upsertUserProfile(
                    uid = signedInUser.uid,
                    email = signedInUser.email.orEmpty(),
                    displayName = signedInUser.displayName.orEmpty(),
                )
                loginHistoryRepository.recordLogin(signedInUser.uid, currentLoginDevice())
                _user.value = signedInUser
            }.onFailure { e ->
                if (e is CancellationException) throw e
            }.fold(
                onSuccess = { onResult(true) },
                onFailure = {
                    // signInWithCredential이 이미 성공해 Firebase 세션이 생겼을 수 있다(authStateListener가
                    // 비동기로 _user를 갱신). 프로필 upsert/로그인 기록 실패를 로그인 실패로 취급하려면
                    // 그 세션을 실제로 롤백해야 _user와 onResult(false)가 어긋나지 않는다.
                    auth.signOut()
                    _user.value = null
                    onResult(false)
                },
            )
        }
    }

    private fun currentLoginDevice() = LoginDevice(
        osVersion = Build.VERSION.RELEASE.orEmpty(),
        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
        appVersion = BuildConfig.VERSION_NAME,
    )

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
