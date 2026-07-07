package com.jkapp.notification

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.jkapp.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class JkFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pushTokenManager = PushTokenManager()

    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification ?: return
        showNotification(notification.title, notification.body)
    }

    // 앱 재설치·토큰 만료 시 FCM이 새 토큰을 발급하며 호출된다. 포그라운드 전환 갱신 로직과
    // 동일한 경로(updateTokenIfChanged)를 태워 두 곳에서 갱신 규칙이 어긋나지 않게 한다.
    override fun onNewToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        serviceScope.launch {
            pushTokenManager.updateTokenIfChanged(uid, token)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun showNotification(title: String?, body: String?) {
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_TODO_ASSIGNMENT)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
