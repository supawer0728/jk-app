package com.jkapp.todo

// 마감일(dueAt)/reminderOffsetMinutes 기반 알림 예약을 담당한다. 실제 구현은 #55에서 연결한다.
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
