package com.jkapp.todo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jkapp.R
import com.jkapp.notification.CHANNEL_ID_TODO_REMINDER

// 예약 시각에 실행되어, 항목이 여전히 존재하고 미완료일 때만 마감일 리마인더 알림을 띄운다.
// 생성자는 WorkManager 기본 WorkerFactory가 리플렉션으로 찾는 (Context, WorkerParameters) 시그니처로
// 고정한다 — Repository를 생성자로 주입하면 그 2-인자 생성자가 사라져 인스턴스화에 실패한다. DI 프레임워크가
// 없는 기존 컨벤션대로 Repository는 내부에서 직접 생성하고, 판정 로직은 순수 함수로 추출해 테스트한다.
class TodoReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val repository: TodoFirestoreRepository = TodoFirestoreRepositoryImpl()

    override suspend fun doWork(): Result {
        val itemId = inputData.getString(KEY_TODO_ITEM_ID) ?: return Result.failure()
        // 조회 자체가 실패(네트워크 등)하면 재시도하되, 마감 리마인더는 시의성이 중요하므로 무한정
        // 재시도하지 않는다. 상한을 넘으면 낡은 알림이 수 시간 뒤 뜨는 것을 막기 위해 포기한다.
        val item = runCatching { repository.getTodoItemOnce(itemId) }
            .getOrElse {
                return if (runAttemptCount + 1 < MAX_RETRY_ATTEMPTS) Result.retry() else Result.success()
            }
        if (shouldShowReminderNotification(item)) {
            showNotification(itemId, requireNotNull(item))
        }
        return Result.success()
    }

    private fun showNotification(itemId: String, item: TodoItem) {
        val context = applicationContext
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_TODO_REMINDER)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.todo_reminder_notification_title))
            .setContentText(item.title)
            .setAutoCancel(true)
            .build()
        // 알림 tag를 항목 id로 지정해, 서로 다른 항목의 리마인더가 겹치지 않으면서(hashCode 충돌 없음)
        // 같은 항목의 재알림은 이전 알림을 대체하도록 한다.
        NotificationManagerCompat.from(context).notify(itemId, REMINDER_NOTIFICATION_ID, notification)
    }

    companion object {
        const val KEY_TODO_ITEM_ID = "todo_item_id"
        // 알림 tag(항목 id)로 항목을 구분하므로 numeric id는 고정값을 쓴다. FCM 배정 알림(1001)과 겹치지 않게 한다.
        private const val REMINDER_NOTIFICATION_ID = 2001
        // 조회 실패 시 최대 재시도 횟수(초기 시도 포함). 상한 도달 시 포기해 낡은 알림을 막는다.
        private const val MAX_RETRY_ATTEMPTS = 3
    }
}
