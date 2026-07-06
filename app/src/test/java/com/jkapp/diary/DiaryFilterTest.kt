package com.jkapp.diary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiaryFilterTest {

    // region toggleInSet

    @Test
    fun `toggleInSet adds id when not present`() {
        val result = DiaryViewModel.toggleInSet("A", emptySet())
        assertEquals(setOf("A"), result)
    }

    @Test
    fun `toggleInSet removes id when already present`() {
        val result = DiaryViewModel.toggleInSet("A", setOf("A", "B"))
        assertEquals(setOf("B"), result)
    }

    @Test
    fun `toggleInSet on empty set results in single element set`() {
        val result = DiaryViewModel.toggleInSet("X", emptySet())
        assertEquals(1, result.size)
        assertTrue("X" in result)
    }

    @Test
    fun `toggleInSet last element results in empty set`() {
        val result = DiaryViewModel.toggleInSet("A", setOf("A"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `toggleInSet does not mutate other elements`() {
        val original = setOf("A", "B", "C")
        val result = DiaryViewModel.toggleInSet("B", original)
        assertEquals(setOf("A", "C"), result)
    }

    // endregion

    // region filterRecords

    private fun record(type: String) = CatRecord(
        firestoreId = null,
        date = "2025-01-01",
        recordType = type,
        record = "내용"
    )

    @Test
    fun `filterRecords returns all records when selectedTypeIds is empty`() {
        val records = listOf(record("DAILY_NOTE"), record("HOSPITAL_VISIT"))
        val result = DiaryViewModel.filterRecords(records, emptySet())
        assertEquals(records, result)
    }

    @Test
    fun `filterRecords keeps only matching type`() {
        val records = listOf(record("DAILY_NOTE"), record("HOSPITAL_VISIT"))
        val result = DiaryViewModel.filterRecords(records, setOf("DAILY_NOTE"))
        assertEquals(listOf(record("DAILY_NOTE")), result)
    }

    @Test
    fun `filterRecords is case-insensitive`() {
        val records = listOf(record("daily_note"), record("HOSPITAL_VISIT"))
        val result = DiaryViewModel.filterRecords(records, setOf("DAILY_NOTE"))
        assertEquals(listOf(record("daily_note")), result)
    }

    @Test
    fun `filterRecords trims whitespace in stored recordType`() {
        val records = listOf(record(" DAILY_NOTE "), record("HOSPITAL_VISIT"))
        val result = DiaryViewModel.filterRecords(records, setOf("DAILY_NOTE"))
        assertEquals(listOf(record(" DAILY_NOTE ")), result)
    }

    @Test
    fun `filterRecords trims whitespace in selectedTypeIds`() {
        val records = listOf(record("DAILY_NOTE"), record("HOSPITAL_VISIT"))
        val result = DiaryViewModel.filterRecords(records, setOf(" DAILY_NOTE "))
        assertEquals(listOf(record("DAILY_NOTE")), result)
    }

    @Test
    fun `filterRecords returns empty list when no records match`() {
        val records = listOf(record("DAILY_NOTE"), record("HOSPITAL_VISIT"))
        val result = DiaryViewModel.filterRecords(records, setOf("WEIGHT"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `filterRecords supports multiple selected types`() {
        val records = listOf(record("DAILY_NOTE"), record("HOSPITAL_VISIT"), record("WEIGHT"))
        val result = DiaryViewModel.filterRecords(records, setOf("DAILY_NOTE", "WEIGHT"))
        assertEquals(listOf(record("DAILY_NOTE"), record("WEIGHT")), result)
    }

    // endregion

    // region sortedDistinctDates

    private fun dateRecord(date: String, type: String = "DAILY_NOTE") = CatRecord(
        firestoreId = null,
        date = date,
        recordType = type,
        record = "내용"
    )

    @Test
    fun `sortedDistinctDates sorts ascending so right swipe reaches earlier dates first`() {
        val records = listOf(dateRecord("2025-03-01"), dateRecord("2025-01-15"), dateRecord("2025-02-10"))
        val result = DiaryViewModel.sortedDistinctDates(records, emptySet())
        assertEquals(listOf("2025-01-15", "2025-02-10", "2025-03-01"), result)
    }

    @Test
    fun `sortedDistinctDates removes duplicate dates`() {
        val records = listOf(dateRecord("2025-01-01"), dateRecord("2025-01-01"), dateRecord("2025-01-02"))
        val result = DiaryViewModel.sortedDistinctDates(records, emptySet())
        assertEquals(listOf("2025-01-01", "2025-01-02"), result)
    }

    @Test
    fun `sortedDistinctDates applies type filter before sorting`() {
        val records = listOf(
            dateRecord(date = "2025-01-03", type = "DAILY_NOTE"),
            dateRecord(date = "2025-01-01", type = "HOSPITAL_VISIT"),
            dateRecord(date = "2025-01-02", type = "DAILY_NOTE"),
        )
        val result = DiaryViewModel.sortedDistinctDates(records, setOf("DAILY_NOTE"))
        assertEquals(listOf("2025-01-02", "2025-01-03"), result)
    }

    // endregion

    // region SYSTEM_TYPE_IDS

    @Test
    fun `SYSTEM_TYPE_IDS contains DAILY_NOTE`() {
        assertTrue("DAILY_NOTE" in DiaryViewModel.SYSTEM_TYPE_IDS)
    }

    @Test
    fun `SYSTEM_TYPE_IDS contains HOSPITAL_VISIT`() {
        assertTrue("HOSPITAL_VISIT" in DiaryViewModel.SYSTEM_TYPE_IDS)
    }

    @Test
    fun `SYSTEM_TYPE_IDS has exactly two entries`() {
        assertEquals(2, DiaryViewModel.SYSTEM_TYPE_IDS.size)
    }

    @Test
    fun `arbitrary id is not a system type`() {
        assertFalse("WEIGHT" in DiaryViewModel.SYSTEM_TYPE_IDS)
    }

    // endregion
}
