package com.jkapp.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.content.getSystemService
import com.jkapp.R
import com.jkapp.common.NotificationMode
import com.jkapp.common.NotificationSound

const val CHANNEL_ID_TODO_ASSIGNMENT = "todo_assignment"
// 리마인더는 방식(소리/진동/OFF)·알림음별로 별도 채널을 지연 생성한다(채널은 생성 후 소리/진동이
// 불변이므로). 이 상수는 하위 호환/기본 채널 식별용 prefix로만 쓴다.
const val CHANNEL_ID_TODO_REMINDER = "todo_reminder"

// 앱의 모든 알림 채널을 한 곳에서 생성해, 채널 초기화 코드가 여러 곳으로 흩어지지 않게 한다.
// 리마인더 채널은 설정에 따라 달라지므로 여기서 만들지 않고 ensureReminderChannel로 필요 시 만든다.
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

// 방식·알림음 조합에 해당하는 리마인더 채널을 (없으면) 생성하고 채널 id를 반환한다.
// 이미 만든 채널은 소리/진동이 고정되므로 재사용한다. 사용자가 설정을 바꾸면 다른 조합의 새 채널로 라우팅된다.
fun ensureReminderChannel(
    context: Context,
    mode: NotificationMode,
    sound: NotificationSound,
): String {
    val channelId = reminderChannelId(mode, sound)
    val manager = context.getSystemService<NotificationManager>() ?: return channelId
    if (manager.getNotificationChannel(channelId) != null) return channelId

    // OFF는 조용히(무음, 낮은 중요도) 알림만 표시한다.
    val importance = if (mode == NotificationMode.OFF) {
        NotificationManager.IMPORTANCE_LOW
    } else {
        NotificationManager.IMPORTANCE_DEFAULT
    }
    val channel = NotificationChannel(
        channelId,
        context.getString(R.string.notification_channel_todo_reminder_name),
        importance,
    ).apply {
        description = context.getString(R.string.notification_channel_todo_reminder_description)
        if (mode.hasSound) {
            val attributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()
            setSound(sound.toUri(), attributes)
        } else {
            setSound(null, null)
        }
        enableVibration(mode.hasVibration)
    }
    manager.createNotificationChannel(channel)
    return channelId
}

// 소리 없는 방식(진동만/OFF)은 알림음이 채널을 구분할 필요가 없어 id에서 제외해 채널 난립을 줄인다.
private fun reminderChannelId(mode: NotificationMode, sound: NotificationSound): String =
    if (mode.hasSound) "${CHANNEL_ID_TODO_REMINDER}_${mode.name}_${sound.name}"
    else "${CHANNEL_ID_TODO_REMINDER}_${mode.name}"

// 프리셋을 시스템 기본 사운드 URI로 해석한다(번들 오디오 없음). 기본음이 없으면 null(무음).
private fun NotificationSound.toUri(): Uri? = when (this) {
    NotificationSound.DEFAULT -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    NotificationSound.ALARM -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    NotificationSound.RINGTONE -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
}
