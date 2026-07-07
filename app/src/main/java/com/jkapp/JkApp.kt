package com.jkapp

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.jkapp.common.AppFirestore
import com.jkapp.notification.PushTokenManager
import com.jkapp.notification.registerNotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class JkApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pushTokenManager = PushTokenManager()

    override fun onCreate() {
        super.onCreate()
        AppFirestore.instance.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
            .build()
        registerNotificationChannels(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(pushTokenRefreshObserver)
    }

    // 포그라운드 전환마다 로그인 상태면 토큰을 갱신한다. FirebaseAuth를 직접 참조하는 이유는
    // Application이 Activity 스코프의 AuthViewModel을 가질 수 없어서다(둘 다 같은 인증 세션을 반영).
    private val pushTokenRefreshObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            applicationScope.launch {
                pushTokenManager.refreshTokenIfNeeded(uid)
            }
        }
    }
}
