package com.jkapp.ui

import com.jkapp.data.model.CatRecord
import com.jkapp.data.model.CatRecordType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiaryUiStateTest {

    @Test
    fun `Loading은 싱글턴이다`() {
        val a: DiaryUiState = DiaryUiState.Loading
        val b: DiaryUiState = DiaryUiState.Loading
        assertEquals(a, b)
    }

    @Test
    fun `Success는 같은 records와 recordTypes를 가지면 동등하다`() {
        val records = listOf(CatRecord(date = "2024-01-01", recordType = "DAILY_NOTE", record = "메모"))
        val types = listOf(makeType("DAILY_NOTE", "일상"))
        assertEquals(DiaryUiState.Success(records, types), DiaryUiState.Success(records, types))
    }

    @Test
    fun `Success는 records가 다르면 다르다`() {
        val types = listOf(makeType("DAILY_NOTE", "일상"))
        val a = DiaryUiState.Success(
            listOf(CatRecord(date = "2024-01-01", recordType = "DAILY_NOTE", record = "A")), types,
        )
        val b = DiaryUiState.Success(
            listOf(CatRecord(date = "2024-01-01", recordType = "DAILY_NOTE", record = "B")), types,
        )
        assertNotEquals(a, b)
    }

    @Test
    fun `Error는 message를 담고 있다`() {
        val error = DiaryUiState.Error("오류 발생")
        assertEquals("오류 발생", error.message)
    }

    @Test
    fun `Error는 같은 message를 가지면 동등하다`() {
        assertEquals(DiaryUiState.Error("err"), DiaryUiState.Error("err"))
    }

    @Test
    fun `when 분기에서 sealed interface의 세 가지 타입이 올바르게 구분된다`() {
        fun label(state: DiaryUiState) = when (state) {
            is DiaryUiState.Loading -> "loading"
            is DiaryUiState.Success -> "success"
            is DiaryUiState.Error -> "error"
        }
        assertEquals("loading", label(DiaryUiState.Loading))
        assertEquals("success", label(DiaryUiState.Success(emptyList(), emptyList())))
        assertEquals("error", label(DiaryUiState.Error("err")))
    }

    private fun makeType(id: String, name: String) = CatRecordType(
        id = id, name = name, emoji = "📝", fontColor = "#000000", backgroundColor = "#FFFFFF",
    )
}
