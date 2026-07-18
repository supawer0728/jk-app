package com.jkapp.todo

import java.time.Instant
import java.time.ZoneOffset

data class TodoItem(
    val firestoreId: String? = null,
    // 항목 종류. MAIN이 기본값이며, 자식 항목은 SUB. 레거시 문서(type 필드 없음)는 MAIN으로 처리한다.
    val type: TodoType = TodoType.MAIN,
    // 자식(SUB) 항목일 때 부모 문서 ID. MAIN이면 null.
    val mainTodoId: String? = null,
    val title: String,
    val memo: String = "",
    val status: TodoStatus = TodoStatus.NOT_STARTED,
    val assignee: TodoAssignee = TodoAssignee.DEFAULT,
    val dueAt: Instant? = null,
    val reminderOffsetMinutes: Int? = null,
    val priority: TodoPriority = TodoPriority.NONE,
    val recurrence: RecurrenceRule? = null,
    val completionHistory: List<Instant> = emptyList(),
    val createdAt: Instant? = null,
    val completedAt: Instant? = null,
    // 마지막으로 저장한 사용자의 Firebase Auth uid. Repository가 쓰기 시점에 주입한다.
    // Firestore 트리거(Cloud Functions)는 "누가 썼는지"를 모르므로, 담당자 알림에서
    // 편집자 본인을 대상에서 제외하는 판별용으로 이 값을 저장한다(이슈 #60 ADR).
    val lastEditedByUid: String? = null,
) {
    // isCompleted는 status == DONE의 파생값이다(이슈 #71 ADR). 리마인더 예약 조건·완료 필터 등
    // 기존 호출부가 이 프로퍼티를 그대로 쓸 수 있게 유지한다.
    val isCompleted: Boolean get() = status == TodoStatus.DONE
}

// 완료 처리 시 반복 항목은 dueAt을 다음 회차로 in-place 전진시키고 상태를 NOT_STARTED로 리셋한다.
// 지나간 dueAt은 completionHistory에 쌓이고, 다음 회차가 endAt을 넘어서면 반복을 종료하며
// status=DONE으로 고정한다. 반복이 없는 항목은 단순히 완료(DONE) 처리한다.
fun TodoItem.completeOccurrence(completedInstant: Instant): TodoItem {
    val rule = recurrence ?: return copy(status = TodoStatus.DONE, completedAt = completedInstant)
    val currentDueAt = dueAt ?: completedInstant
    val nextDueAt = rule.nextDueAt(currentDueAt)
    val recurrenceEnded = rule.endAt?.let { nextDueAt.isAfter(it) } ?: false
    return copy(
        dueAt = if (recurrenceEnded) currentDueAt else nextDueAt,
        status = if (recurrenceEnded) TodoStatus.DONE else TodoStatus.NOT_STARTED,
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
