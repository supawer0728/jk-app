package com.jkapp.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import com.jkapp.R

const val CHANNEL_ID_TODO_ASSIGNMENT = "todo_assignment"
const val CHANNEL_ID_TODO_REMINDER = "todo_reminder"

// 앱의 모든 알림 채널을 한 곳에서 생성해, 채널 초기화 코드가 여러 곳으로 흩어지지 않게 한다.
fun registerNotificationChannels(context: Context) {
    val notificationManager = context.getSystemService<NotificationManager>() ?: return
    val todoAssignmentChannel = NotificationChannel(
        CHANNEL_ID_TODO_ASSIGNMENT,
        context.getString(R.string.notification_channel_todo_assignment_name),
        NotificationManager.IMPORTANCE_DEFAULT,
    ).apply {
        description = context.getString(R.string.notification_channel_todo_assignment_description)
    }
    val todoReminderChannel = NotificationChannel(
        CHANNEL_ID_TODO_REMINDER,
        context.getString(R.string.notification_channel_todo_reminder_name),
        NotificationManager.IMPORTANCE_DEFAULT,
    ).apply {
        description = context.getString(R.string.notification_channel_todo_reminder_description)
    }
    notificationManager.createNotificationChannel(todoAssignmentChannel)
    notificationManager.createNotificationChannel(todoReminderChannel)
}
