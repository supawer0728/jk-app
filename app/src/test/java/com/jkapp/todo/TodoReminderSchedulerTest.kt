package com.jkapp.todo

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// TodoReminderScheduler.kt의 순수 로직만 검증한다. WorkManager 예약 자체는 이 프로젝트의
// 순수 JVM 단위 테스트 환경(Robolectric/계측 테스트 없음)에서 구동할 수 없어 제외한다.
class TodoReminderSchedulerTest {

    private val due = Instant.parse("2026-07-11T09:00:00Z")

    @Test
    fun `reminderWorkName은 firestoreId 기반의 고유 이름을 만든다`() {
        assertEquals("todo-reminder-abc123", reminderWorkName("abc123"))
    }

    @Test
    fun `reminderTriggerAt은 마감시각에서 오프셋만큼 앞당긴 시각이다`() {
        assertEquals(Instant.parse("2026-07-11T08:30:00Z"), reminderTriggerAt(due, 30))
    }

    @Test
    fun `reminderTriggerAt은 오프셋 0이면 마감시각과 같다`() {
        assertEquals(due, reminderTriggerAt(due, 0))
    }

    @Test
    fun `reminderDelayMillis는 now부터 예약 시각까지 남은 밀리초다`() {
        val now = Instant.parse("2026-07-11T08:00:00Z")
        // triggerAt = 08:30, now = 08:00 -> 30분 = 1_800_000ms
        assertEquals(1_800_000L, reminderDelayMillis(due, 30, now))
    }

    @Test
    fun `reminderDelayMillis는 예약 시각이 지났으면 음수다`() {
        val now = Instant.parse("2026-07-11T08:45:00Z")
        // triggerAt = 08:30, now = 08:45 -> -15분
        assertEquals(-900_000L, reminderDelayMillis(due, 30, now))
    }

    @Test
    fun `shouldEnqueueReminder는 예약 시각이 미래일 때만 true다`() {
        assertTrue(shouldEnqueueReminder(due, 30, Instant.parse("2026-07-11T08:00:00Z")))
        assertFalse(shouldEnqueueReminder(due, 30, Instant.parse("2026-07-11T08:45:00Z")))
    }

    @Test
    fun `shouldEnqueueReminder는 예약 시각과 now가 같으면 false다`() {
        // triggerAt == now 인 경계는 예약하지 않는다(delay > 0 아님).
        assertFalse(shouldEnqueueReminder(due, 30, Instant.parse("2026-07-11T08:30:00Z")))
    }

    @Test
    fun `shouldShowReminderNotification은 미완료 항목에 대해 true다`() {
        val item = TodoItem(firestoreId = "id1", title = "제출", isCompleted = false)
        assertTrue(shouldShowReminderNotification(item))
    }

    @Test
    fun `shouldShowReminderNotification은 완료된 항목이면 false다`() {
        val item = TodoItem(firestoreId = "id1", title = "제출", isCompleted = true)
        assertFalse(shouldShowReminderNotification(item))
    }

    @Test
    fun `shouldShowReminderNotification은 항목이 없으면(삭제됨) false다`() {
        assertFalse(shouldShowReminderNotification(null))
    }
}
