package com.jkapp.ui

import com.jkapp.data.model.CatRecordType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiaryRecordTypeValidationTest {

    private fun type(id: String) = CatRecordType(
        id = id,
        name = "테스트",
        emoji = "📝",
        fontColor = "#000000",
        backgroundColor = "#FFFFFF",
    )

    // region validateNewRecordType

    @Test
    fun `validateNewRecordType returns null for valid new type`() {
        val result = DiaryViewModel.validateNewRecordType(type("WEIGHT"), emptyList())
        assertNull(result)
    }

    @Test
    fun `validateNewRecordType returns error for DAILY_NOTE system id`() {
        val result = DiaryViewModel.validateNewRecordType(type("DAILY_NOTE"), emptyList())
        assertEquals("시스템 필수 유형 ID는 사용할 수 없습니다.", result)
    }

    @Test
    fun `validateNewRecordType returns error for HOSPITAL_VISIT system id`() {
        val result = DiaryViewModel.validateNewRecordType(type("HOSPITAL_VISIT"), emptyList())
        assertEquals("시스템 필수 유형 ID는 사용할 수 없습니다.", result)
    }

    @Test
    fun `validateNewRecordType returns error for duplicate id`() {
        val result = DiaryViewModel.validateNewRecordType(type("WEIGHT"), listOf("WEIGHT", "FOOD"))
        assertEquals("이미 존재하는 기록유형 ID입니다: WEIGHT", result)
    }

    @Test
    fun `validateNewRecordType system id check takes priority over duplicate`() {
        val result = DiaryViewModel.validateNewRecordType(type("DAILY_NOTE"), listOf("DAILY_NOTE"))
        assertEquals("시스템 필수 유형 ID는 사용할 수 없습니다.", result)
    }

    @Test
    fun `validateNewRecordType returns null when id not in existing list`() {
        val result = DiaryViewModel.validateNewRecordType(type("NEW_TYPE"), listOf("WEIGHT", "FOOD"))
        assertNull(result)
    }

    // endregion

    // region validateDeleteRecordType

    @Test
    fun `validateDeleteRecordType returns null for non-system type`() {
        val result = DiaryViewModel.validateDeleteRecordType("WEIGHT")
        assertNull(result)
    }

    @Test
    fun `validateDeleteRecordType returns error for DAILY_NOTE`() {
        val result = DiaryViewModel.validateDeleteRecordType("DAILY_NOTE")
        assertEquals("시스템 필수 유형은 삭제할 수 없습니다.", result)
    }

    @Test
    fun `validateDeleteRecordType returns error for HOSPITAL_VISIT`() {
        val result = DiaryViewModel.validateDeleteRecordType("HOSPITAL_VISIT")
        assertEquals("시스템 필수 유형은 삭제할 수 없습니다.", result)
    }

    @Test
    fun `validateDeleteRecordType returns null when typeId is null`() {
        // type not found in current state → null typeId → delete proceeds
        val result = DiaryViewModel.validateDeleteRecordType(null)
        assertNull(result)
    }

    // endregion
}
