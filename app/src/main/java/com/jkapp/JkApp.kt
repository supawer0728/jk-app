package com.jkapp

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.jkapp.common.AppFirestore
import com.jkapp.common.AppPreferences
import com.jkapp.notification.PushTokenManager
import com.jkapp.notification.registerNotificationChannels
import com.jkapp.push.PushCleanupScheduler
import com.jkapp.push.PushRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class JkApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // FirebaseApp은 FirebaseInitProvider(ContentProvider)가 초기화하며, 이는 Application 생성자보다
    // 나중에 실행된다. 즉시 초기화하면 PushTokenManager -> UserRepositoryImpl -> AppFirestore.instance가
    // Application 생성자 시점에 평가되어 "Default FirebaseApp is not initialized" 크래시가 난다.
    private val pushTokenManager by lazy { PushTokenManager() }
    private val appPreferences by lazy { AppPreferences(this) }
    // 30일 지난 pushes 문서 정리(이슈 #89). 마지막 실행 날짜는 AppPreferences(DataStore)에 캐싱해
    // 앱 시작/포그라운드 복귀마다 호출돼도 하루 1회만 실제로 실행되게 한다.
    private val pushCleanupScheduler by lazy {
        PushCleanupScheduler(
            pushRepository = PushRepositoryImpl(),
            getLastCleanupDate = { appPreferences.lastPushCleanupDate.first() },
            setLastCleanupDate = { appPreferences.setLastPushCleanupDate(it) },
        )
    }

    override fun onCreate() {
        super.onCreate()
        AppFirestore.instance.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
            .build()
        registerNotificationChannels(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(pushTokenRefreshObserver)
    }

    // 포그라운드 전환마다(앱 최초 시작 포함) 로그인 상태면 토큰을 갱신하고, pushes 정리를 시도한다.
    // FirebaseAuth를 직접 참조하는 이유는 Application이 Activity 스코프의 AuthViewModel을 가질 수
    // 없어서다(둘 다 같은 인증 세션을 반영). pushes 보안 규칙이 인증을 요구하므로 정리도 로그인
    // 상태에서만 실행한다(미로그인 콜드스타트에서 PERMISSION_DENIED로 크래시하지 않도록).
    private val pushTokenRefreshObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            applicationScope.launch {
                pushTokenManager.refreshTokenIfNeeded(uid)
            }
            applicationScope.launch {
                // 정리 실패(권한/네트워크 등)가 앱을 크래시시키지 않도록 예외를 삼키고 기록만 한다.
                runCatching { pushCleanupScheduler.runIfNeeded() }
                    .onFailure { android.util.Log.w("jkapp", "pushes 정리 실패", it) }
            }
        }
    }
}
