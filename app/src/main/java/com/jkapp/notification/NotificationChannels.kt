package com.jkapp.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import com.jkapp.R

const val CHANNEL_ID_TODO_ASSIGNMENT = "todo_assignment"

// #55의 todo_reminder 채널도 여기에 함께 추가해, 채널 생성 초기화 코드가 여러 곳으로 흩어지지 않게 한다.
fun registerNotificationChannels(context: Context) {
    val notificationManager = context.getSystemService<NotificationManager>() ?: return
    val todoAssignmentChannel = NotificationChannel(
        CHANNEL_ID_TODO_ASSIGNMENT,
        context.getString(R.string.notification_channel_todo_assignment_name),
        NotificationManager.IMPORTANCE_DEFAULT,
    ).apply {
        description = context.getString(R.string.notification_channel_todo_assignment_description)
    }
    notificationManager.createNotificationChannel(todoAssignmentChannel)
}
