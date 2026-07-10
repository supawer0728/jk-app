package com.jkapp.todo

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Instant
import java.util.concurrent.TimeUnit

// WorkManager 기반 리마인더 예약. 재부팅/앱 종료 후에도 WorkManager가 예약을 자동으로 유지·재등록한다.
// 항목별 unique work 이름(reminderWorkName)으로 REPLACE 예약하므로, 같은 항목의 재예약은 이전 예약을 대체한다.
class TodoReminderSchedulerImpl(context: Context) : TodoReminderScheduler {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun schedule(item: TodoItem) {
        val firestoreId = item.firestoreId ?: return
        val dueAt = item.dueAt ?: return
        val offset = item.reminderOffsetMinutes ?: return
        val now = Instant.now()
        if (!shouldEnqueueReminder(dueAt, offset, now)) {
            // 예약 시각이 이미 지났으면(과거 항목 편집 등) 예약하지 않고, 남아있을 수 있는 이전 예약만 취소한다.
            workManager.cancelUniqueWork(reminderWorkName(firestoreId))
            return
        }
        val request = OneTimeWorkRequestBuilder<TodoReminderWorker>()
            .setInitialDelay(reminderDelayMillis(dueAt, offset, now), TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder().putString(TodoReminderWorker.KEY_TODO_ITEM_ID, firestoreId).build()
            )
            .build()
        workManager.enqueueUniqueWork(
            reminderWorkName(firestoreId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    override fun cancel(item: TodoItem) {
        val firestoreId = item.firestoreId ?: return
        workManager.cancelUniqueWork(reminderWorkName(firestoreId))
    }
}
