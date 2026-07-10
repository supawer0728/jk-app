package com.jkapp.todo

import java.time.Instant

// 마감일(dueAt)/reminderOffsetMinutes 기반 알림 예약을 담당한다. WorkManager 구현은 TodoReminderSchedulerImpl.
// TodoViewModel.toggleCompleted는 매 상태 변경마다 cancel(item)을 먼저 호출한 뒤 필요 시 schedule(updated)을
// 호출하므로, 이 인터페이스의 구현체는 스스로 dueAt 변경분을 dedup할 필요가 없다 — firestoreId 기준으로
// 이전 예약을 취소하고 있으면 존재하지 않는 예약에 대한 cancel 호출도 안전하게(no-op) 무시해야 한다.
interface TodoReminderScheduler {
    fun schedule(item: TodoItem)
    fun cancel(item: TodoItem)

    companion object {
        val NoOp: TodoReminderScheduler = object : TodoReminderScheduler {
            override fun schedule(item: TodoItem) {}
            override fun cancel(item: TodoItem) {}
        }
    }
}

// --- 순수 로직 (WorkManager/안드로이드 의존 없이 JVM 단위 테스트로 검증) ---

// 항목별 unique work 이름. firestoreId 기준이라 같은 항목의 재예약은 이전 예약을 대체한다.
fun reminderWorkName(firestoreId: String): String = "todo-reminder-$firestoreId"

// 알림이 떠야 하는 시각 = 마감시각 - 오프셋(분).
fun reminderTriggerAt(dueAt: Instant, reminderOffsetMinutes: Int): Instant =
    dueAt.minusSeconds(reminderOffsetMinutes.toLong() * 60)

// 예약까지 남은 지연(ms). now 이후면 양수, 이미 지났으면 0 이하.
fun reminderDelayMillis(dueAt: Instant, reminderOffsetMinutes: Int, now: Instant): Long =
    reminderTriggerAt(dueAt, reminderOffsetMinutes).toEpochMilli() - now.toEpochMilli()

// 예약 시각이 미래(now 초과)일 때만 예약한다. 이미 지난 시각은 즉시 알림을 띄우지 않고 스킵한다.
fun shouldEnqueueReminder(dueAt: Instant, reminderOffsetMinutes: Int, now: Instant): Boolean =
    reminderDelayMillis(dueAt, reminderOffsetMinutes, now) > 0

// 예약된 Worker가 실제로 알림을 띄워야 하는지. 예약 후 삭제/완료됐으면(또는 문서가 사라졌으면) 스킵한다.
fun shouldShowReminderNotification(item: TodoItem?): Boolean =
    item != null && !item.isCompleted
