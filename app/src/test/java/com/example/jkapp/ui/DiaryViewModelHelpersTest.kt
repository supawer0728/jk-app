package com.jkapp.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DiaryViewModelHelpersTest {

    @Test
    fun `todayDate returns current date in ISO format`() {
        val today = DiaryViewModel.todayDate()
        val expected = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        assertEquals(expected, today)
    }

    @Test
    fun `todayDate returns yyyy-MM-dd format`() {
        val today = DiaryViewModel.todayDate()
        assertTrue(
            "Date must match yyyy-MM-dd pattern",
            today.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))
        )
    }

    @Test
    fun `computeDayOfWeek returns Korean day name for Monday`() {
        val result = DiaryViewModel.computeDayOfWeek("2025-01-06")
        assertEquals("월", result)
    }

    @Test
    fun `computeDayOfWeek returns Korean day name for Friday`() {
        val result = DiaryViewModel.computeDayOfWeek("2025-01-10")
        assertEquals("금", result)
    }

    @Test
    fun `computeDayOfWeek returns Korean day name for Sunday`() {
        val result = DiaryViewModel.computeDayOfWeek("2025-01-05")
        assertEquals("일", result)
    }

    @Test
    fun `computeDayOfWeek handles leap year date`() {
        val result = DiaryViewModel.computeDayOfWeek("2024-02-29")
        assertEquals("목", result)
    }
}
