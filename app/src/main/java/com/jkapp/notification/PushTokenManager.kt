package com.jkapp.notification

import com.google.firebase.messaging.FirebaseMessaging
import com.jkapp.common.await
import com.jkapp.user.PushToken
import com.jkapp.user.UserRepository
import com.jkapp.user.UserRepositoryImpl

class PushTokenManager(
    private val userRepository: UserRepository = UserRepositoryImpl(),
    private val firebaseMessaging: FirebaseMessaging = FirebaseMessaging.getInstance(),
) {

    suspend fun refreshTokenIfNeeded(uid: String) {
        val token = firebaseMessaging.token.await()
        updateTokenIfChanged(uid, token)
    }

    // 기존 토큰과 동일하면 쓰기를 건너뛴다 - 포그라운드 전환마다 불필요한 Firestore write를 방지한다.
    suspend fun updateTokenIfChanged(uid: String, token: String) {
        val currentToken = userRepository.getPushToken(uid)
        if (currentToken?.token == token) return
        userRepository.updatePushToken(
            uid = uid,
            pushToken = PushToken(token = token, updatedAt = System.currentTimeMillis()),
        )
    }
}
