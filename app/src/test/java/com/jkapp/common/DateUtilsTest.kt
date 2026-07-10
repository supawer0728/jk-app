package com.jkapp.common

import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DateUtilsTest {

    // DatePicker가 넘겨주는 UTC 자정 millis 형식으로 변환한다.
    private fun utcMillis(isoDate: String): Long =
        LocalDate.parse(isoDate, DateTimeFormatter.ISO_LOCAL_DATE)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun `todayDate returns current date in ISO format`() {
        val today = todayDate()
        val expected = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        assertEquals(expected, today)
    }

    @Test
    fun `todayDate returns yyyy-MM-dd format`() {
        val today = todayDate()
        assertTrue(
            "Date must match yyyy-MM-dd pattern",
            today.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))
        )
    }

    @Test
    fun `computeDayOfWeek returns Korean day name for Monday`() {
        val result = computeDayOfWeek("2025-01-06")
        assertEquals("월", result)
    }

    @Test
    fun `computeDayOfWeek returns Korean day name for Friday`() {
        val result = computeDayOfWeek("2025-01-10")
        assertEquals("금", result)
    }

    @Test
    fun `computeDayOfWeek returns Korean day name for Sunday`() {
        val result = computeDayOfWeek("2025-01-05")
        assertEquals("일", result)
    }

    @Test
    fun `computeDayOfWeek handles leap year date`() {
        val result = computeDayOfWeek("2024-02-29")
        assertEquals("목", result)
    }

    @Test
    fun `isNotAfterUtcDay는 기준일 이전 날짜를 허용한다`() {
        assertTrue(isNotAfterUtcDay(utcMillis("2026-07-10"), referenceDate = "2026-07-11"))
    }

    @Test
    fun `isNotAfterUtcDay는 기준일 당일을 허용한다`() {
        assertTrue(isNotAfterUtcDay(utcMillis("2026-07-11"), referenceDate = "2026-07-11"))
    }

    @Test
    fun `isNotAfterUtcDay는 기준일 다음날(미래)을 거부한다`() {
        assertFalse(isNotAfterUtcDay(utcMillis("2026-07-12"), referenceDate = "2026-07-11"))
    }

    @Test
    fun `isNotAfterUtcDay는 기본 기준일로 오늘을 사용해 오늘을 허용하고 내일을 거부한다`() {
        val today = todayDate()
        val tomorrow = LocalDate.parse(today, DateTimeFormatter.ISO_LOCAL_DATE).plusDays(1)
            .format(DateTimeFormatter.ISO_LOCAL_DATE)

        assertTrue(isNotAfterUtcDay(utcMillis(today)))
        assertFalse(isNotAfterUtcDay(utcMillis(tomorrow)))
    }
}
