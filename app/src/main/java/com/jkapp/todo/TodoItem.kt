package com.jkapp.todo

import java.time.Instant
import java.time.ZoneOffset

data class TodoItem(
    val firestoreId: String? = null,
    val title: String,
    val memo: String = "",
    val isCompleted: Boolean = false,
    val dueAt: Instant? = null,
    val reminderOffsetMinutes: Int? = null,
    val priority: TodoPriority = TodoPriority.NONE,
    val categoryId: String? = null,
    val tags: List<String> = emptyList(),
    val recurrence: RecurrenceRule? = null,
    val completionHistory: List<Instant> = emptyList(),
    val createdAt: Instant? = null,
    val completedAt: Instant? = null,
)

// 완료 체크 시 반복 항목은 dueAt을 다음 회차로 in-place 전진시키고 isCompleted를 false로 리셋한다.
// 지나간 dueAt은 completionHistory에 쌓이고, 다음 회차가 endAt을 넘어서면 반복을 종료하며
// isCompleted=true로 고정한다. 반복이 없는 항목은 단순히 완료 처리한다.
fun TodoItem.completeOccurrence(completedInstant: Instant): TodoItem {
    val rule = recurrence ?: return copy(isCompleted = true, completedAt = completedInstant)
    val currentDueAt = dueAt ?: completedInstant
    val nextDueAt = rule.nextDueAt(currentDueAt)
    val recurrenceEnded = rule.endAt?.let { nextDueAt.isAfter(it) } ?: false
    return copy(
        dueAt = if (recurrenceEnded) currentDueAt else nextDueAt,
        isCompleted = recurrenceEnded,
        completedAt = completedInstant,
        completionHistory = completionHistory + currentDueAt,
    )
}

// MONTHLY/YEARLY 반복을 처음 저장할 때 dueAt의 day-of-month를 recurrence.anchorDay로 고정한다.
// 이렇게 anchorDay를 한 번 박아두면 이후 말일 클램프가 발생해도(예: 1/31 -> 2/28) 다음 전진에서
// 원래 일자로 복원될 수 있다. anchorDay가 이미 있거나 WEEKLY/DAILY이면 그대로 둔다.
fun TodoItem.withRecurrenceAnchored(): TodoItem {
    val rule = recurrence ?: return this
    val isAnchorable = rule.frequency == RecurrenceFrequency.MONTHLY || rule.frequency == RecurrenceFrequency.YEARLY
    val due = dueAt
    if (rule.anchorDay != null || !isAnchorable || due == null) return this
    return copy(recurrence = rule.copy(anchorDay = due.atZone(ZoneOffset.UTC).dayOfMonth))
}
