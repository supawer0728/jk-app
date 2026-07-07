package com.jkapp.todo

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class RecurrenceRuleTest {

    @Test
    fun `DAILY는 interval일만큼 전진한다`() {
        val rule = RecurrenceRule(frequency = RecurrenceFrequency.DAILY, interval = 3)

        val next = rule.nextDueAt(Instant.parse("2024-01-01T00:00:00Z"))

        assertEquals(Instant.parse("2024-01-04T00:00:00Z"), next)
    }

    @Test
    fun `WEEKLY는 daysOfWeek가 비어 있으면 interval주 단위로 전진한다`() {
        val rule = RecurrenceRule(frequency = RecurrenceFrequency.WEEKLY, interval = 2)

        val next = rule.nextDueAt(Instant.parse("2024-01-01T00:00:00Z"))

        assertEquals(Instant.parse("2024-01-15T00:00:00Z"), next)
    }

    @Test
    fun `WEEKLY는 daysOfWeek가 지정되면 같은 주 내 다음 지정 요일로 전진한다`() {
        // 2024-01-01은 월요일. 월(1)/수(3)/금(5) 반복.
        val rule = RecurrenceRule(frequency = RecurrenceFrequency.WEEKLY, interval = 1, daysOfWeek = setOf(1, 3, 5))

        val fromMonday = rule.nextDueAt(Instant.parse("2024-01-01T00:00:00Z"))
        val fromWednesday = rule.nextDueAt(Instant.parse("2024-01-03T00:00:00Z"))

        assertEquals(Instant.parse("2024-01-03T00:00:00Z"), fromMonday)
        assertEquals(Instant.parse("2024-01-05T00:00:00Z"), fromWednesday)
    }

    @Test
    fun `WEEKLY는 마지막 지정 요일을 지나면 interval주 뒤 첫 지정 요일로 이동한다`() {
        // 2024-01-05는 금요일(마지막 지정 요일).
        val rule = RecurrenceRule(frequency = RecurrenceFrequency.WEEKLY, interval = 1, daysOfWeek = setOf(1, 3, 5))

        val next = rule.nextDueAt(Instant.parse("2024-01-05T00:00:00Z"))

        assertEquals(Instant.parse("2024-01-08T00:00:00Z"), next)
    }

    @Test
    fun `WEEKLY는 현재 요일이 daysOfWeek에 없어도 다음 지정 요일로 전진한다`() {
        // 2024-01-02는 화요일(지정되지 않은 요일). 월(1)/수(3)/금(5) 반복이면 수요일로 전진해야 한다.
        val rule = RecurrenceRule(frequency = RecurrenceFrequency.WEEKLY, interval = 1, daysOfWeek = setOf(1, 3, 5))

        val next = rule.nextDueAt(Instant.parse("2024-01-02T00:00:00Z"))

        assertEquals(Instant.parse("2024-01-03T00:00:00Z"), next)
    }

    @Test
    fun `WEEKLY는 daysOfWeek가 하나뿐이고 interval이 2 이상이면 interval주 뒤로 이동한다`() {
        val rule = RecurrenceRule(frequency = RecurrenceFrequency.WEEKLY, interval = 2, daysOfWeek = setOf(1))

        val next = rule.nextDueAt(Instant.parse("2024-01-01T00:00:00Z"))

        assertEquals(Instant.parse("2024-01-15T00:00:00Z"), next)
    }

    @Test
    fun `MONTHLY는 anchorDay가 없으면 말일 클램프가 누적된다`() {
        val rule = RecurrenceRule(frequency = RecurrenceFrequency.MONTHLY, interval = 1)

        val step1 = rule.nextDueAt(Instant.parse("2026-01-31T00:00:00Z"))
        val step2 = rule.nextDueAt(step1)

        assertEquals(Instant.parse("2026-02-28T00:00:00Z"), step1)
        // anchorDay가 없으면 클램프된 28일이 그대로 다음 계산의 기준이 되어 31일이 복원되지 않는다.
        assertEquals(Instant.parse("2026-03-28T00:00:00Z"), step2)
    }

    @Test
    fun `MONTHLY는 anchorDay가 있으면 클램프 후에도 원래 일자로 복원된다`() {
        val rule = RecurrenceRule(frequency = RecurrenceFrequency.MONTHLY, interval = 1, anchorDay = 31)

        val step1 = rule.nextDueAt(Instant.parse("2026-01-31T00:00:00Z"))
        val step2 = rule.nextDueAt(step1)
        val step3 = rule.nextDueAt(step2)
        val step4 = rule.nextDueAt(step3)

        assertEquals(Instant.parse("2026-02-28T00:00:00Z"), step1)
        assertEquals(Instant.parse("2026-03-31T00:00:00Z"), step2)
        assertEquals(Instant.parse("2026-04-30T00:00:00Z"), step3)
        assertEquals(Instant.parse("2026-05-31T00:00:00Z"), step4)
    }

    @Test
    fun `YEARLY는 anchorDay가 있으면 윤년 2월 29일이 다음 윤년에 복원된다`() {
        val rule = RecurrenceRule(frequency = RecurrenceFrequency.YEARLY, interval = 1, anchorDay = 29)

        val step1 = rule.nextDueAt(Instant.parse("2024-02-29T00:00:00Z"))
        val step2 = rule.nextDueAt(step1)
        val step3 = rule.nextDueAt(step2)
        val step4 = rule.nextDueAt(step3)

        assertEquals(Instant.parse("2025-02-28T00:00:00Z"), step1)
        assertEquals(Instant.parse("2026-02-28T00:00:00Z"), step2)
        assertEquals(Instant.parse("2027-02-28T00:00:00Z"), step3)
        assertEquals(Instant.parse("2028-02-29T00:00:00Z"), step4)
    }

    @Test
    fun `YEARLY는 anchorDay가 없으면 윤년 2월 29일이 복원되지 않는다`() {
        val rule = RecurrenceRule(frequency = RecurrenceFrequency.YEARLY, interval = 1)

        val step1 = rule.nextDueAt(Instant.parse("2024-02-29T00:00:00Z"))
        val step2 = rule.nextDueAt(step1)
        val step3 = rule.nextDueAt(step2)
        val step4 = rule.nextDueAt(step3)

        assertEquals(Instant.parse("2025-02-28T00:00:00Z"), step1)
        assertEquals(Instant.parse("2026-02-28T00:00:00Z"), step2)
        assertEquals(Instant.parse("2027-02-28T00:00:00Z"), step3)
        assertEquals(Instant.parse("2028-02-28T00:00:00Z"), step4)
    }
}
