package com.jkapp.todo

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoItemCompletionTest {

    private fun makeItem(
        dueAt: Instant?,
        recurrence: RecurrenceRule? = null,
    ) = TodoItem(title = "테스트 할일", dueAt = dueAt, recurrence = recurrence)

    @Test
    fun `반복이 없는 항목은 단순히 완료 처리된다`() {
        val dueAt = Instant.parse("2024-01-01T00:00:00Z")
        val item = makeItem(dueAt = dueAt)
        val completedInstant = Instant.parse("2024-01-01T09:00:00Z")

        val result = item.completeOccurrence(completedInstant)

        assertTrue(result.isCompleted)
        assertEquals(completedInstant, result.completedAt)
        assertEquals(dueAt, result.dueAt)
        assertEquals(emptyList<Instant>(), result.completionHistory)
    }

    @Test
    fun `반복 항목은 완료 시 다음 회차로 in-place 전진하고 isCompleted를 리셋한다`() {
        val dueAt = Instant.parse("2024-01-01T00:00:00Z")
        val item = makeItem(
            dueAt = dueAt,
            recurrence = RecurrenceRule(frequency = RecurrenceFrequency.DAILY, interval = 1),
        )
        val completedInstant = Instant.parse("2024-01-01T09:00:00Z")

        val result = item.completeOccurrence(completedInstant)

        assertEquals(Instant.parse("2024-01-02T00:00:00Z"), result.dueAt)
        assertTrue(!result.isCompleted)
        assertEquals(completedInstant, result.completedAt)
        assertEquals(listOf(dueAt), result.completionHistory)
    }

    @Test
    fun `다음 회차가 endAt을 넘어서면 반복을 종료하고 isCompleted를 고정한다`() {
        val dueAt = Instant.parse("2024-01-01T00:00:00Z")
        val item = makeItem(
            dueAt = dueAt,
            recurrence = RecurrenceRule(
                frequency = RecurrenceFrequency.DAILY,
                interval = 1,
                endAt = Instant.parse("2024-01-01T12:00:00Z"),
            ),
        )
        val completedInstant = Instant.parse("2024-01-01T09:00:00Z")

        val result = item.completeOccurrence(completedInstant)

        assertEquals(dueAt, result.dueAt)
        assertTrue(result.isCompleted)
        assertEquals(completedInstant, result.completedAt)
        assertEquals(listOf(dueAt), result.completionHistory)
    }

    @Test
    fun `다음 회차가 endAt과 정확히 같으면 반복이 종료되지 않는다`() {
        val dueAt = Instant.parse("2024-01-01T00:00:00Z")
        val item = makeItem(
            dueAt = dueAt,
            recurrence = RecurrenceRule(
                frequency = RecurrenceFrequency.DAILY,
                interval = 1,
                endAt = Instant.parse("2024-01-02T00:00:00Z"),
            ),
        )

        val result = item.completeOccurrence(Instant.parse("2024-01-01T09:00:00Z"))

        // nextDueAt(2024-01-02T00:00)이 endAt과 정확히 같은 경우 isAfter는 false이므로 종료되지 않는다.
        assertEquals(Instant.parse("2024-01-02T00:00:00Z"), result.dueAt)
        assertTrue(!result.isCompleted)
    }

    @Test
    fun `dueAt이 없는 반복 항목은 completedInstant를 기준으로 다음 회차를 계산한다`() {
        val item = makeItem(
            dueAt = null,
            recurrence = RecurrenceRule(frequency = RecurrenceFrequency.DAILY, interval = 1),
        )
        val completedInstant = Instant.parse("2024-01-01T09:00:00Z")

        val result = item.completeOccurrence(completedInstant)

        assertEquals(Instant.parse("2024-01-02T09:00:00Z"), result.dueAt)
        assertEquals(listOf(completedInstant), result.completionHistory)
    }

    @Test
    fun `여러 회차를 연속 완료하면 completionHistory가 누적된다`() {
        val firstDueAt = Instant.parse("2024-01-01T00:00:00Z")
        val item = makeItem(
            dueAt = firstDueAt,
            recurrence = RecurrenceRule(frequency = RecurrenceFrequency.DAILY, interval = 1),
        )

        val afterFirst = item.completeOccurrence(Instant.parse("2024-01-01T09:00:00Z"))
        val afterSecond = afterFirst.completeOccurrence(Instant.parse("2024-01-02T09:00:00Z"))

        assertEquals(Instant.parse("2024-01-03T00:00:00Z"), afterSecond.dueAt)
        assertEquals(
            listOf(Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-02T00:00:00Z")),
            afterSecond.completionHistory,
        )
    }
}
