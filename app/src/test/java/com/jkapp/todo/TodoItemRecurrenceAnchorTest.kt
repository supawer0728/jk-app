package com.jkapp.todo

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodoItemRecurrenceAnchorTest {

    @Test
    fun `MONTHLY 반복은 anchorDay가 비어 있으면 dueAt의 day-of-month로 채워진다`() {
        val item = TodoItem(
            title = "월세 납부",
            dueAt = Instant.parse("2026-01-31T00:00:00Z"),
            recurrence = RecurrenceRule(frequency = RecurrenceFrequency.MONTHLY, interval = 1),
        )

        val anchored = item.withRecurrenceAnchored()

        assertEquals(31, anchored.recurrence?.anchorDay)
    }

    @Test
    fun `이미 anchorDay가 있으면 덮어쓰지 않는다`() {
        val item = TodoItem(
            title = "월세 납부",
            dueAt = Instant.parse("2026-01-15T00:00:00Z"),
            recurrence = RecurrenceRule(frequency = RecurrenceFrequency.MONTHLY, interval = 1, anchorDay = 31),
        )

        val anchored = item.withRecurrenceAnchored()

        assertEquals(31, anchored.recurrence?.anchorDay)
    }

    @Test
    fun `WEEKLY DAILY 반복이나 반복이 없는 항목은 변경되지 않는다`() {
        val weekly = TodoItem(
            title = "주간 청소",
            dueAt = Instant.parse("2026-01-05T00:00:00Z"),
            recurrence = RecurrenceRule(frequency = RecurrenceFrequency.WEEKLY, interval = 1),
        )
        val noRecurrence = TodoItem(title = "일회성 할일", dueAt = Instant.parse("2026-01-05T00:00:00Z"))

        assertNull(weekly.withRecurrenceAnchored().recurrence?.anchorDay)
        assertEquals(noRecurrence, noRecurrence.withRecurrenceAnchored())
    }

    @Test
    fun `dueAt이 없으면 anchorDay를 채우지 못한다`() {
        val item = TodoItem(
            title = "월세 납부",
            dueAt = null,
            recurrence = RecurrenceRule(frequency = RecurrenceFrequency.MONTHLY, interval = 1),
        )

        val anchored = item.withRecurrenceAnchored()

        assertNull(anchored.recurrence?.anchorDay)
    }
}
