package com.jkapp.todo

import org.junit.Assert.assertEquals
import org.junit.Test

class TodoTypeTest {

    @Test
    fun `fromNameOrDefault는 유효한 이름을 파싱한다`() {
        assertEquals(TodoType.MAIN, TodoType.fromNameOrDefault("MAIN"))
        assertEquals(TodoType.SUB, TodoType.fromNameOrDefault("SUB"))
    }

    @Test
    fun `fromNameOrDefault는 알 수 없는 이름이면 MAIN을 반환한다(레거시 하위 호환)`() {
        assertEquals(TodoType.MAIN, TodoType.fromNameOrDefault(null))
        assertEquals(TodoType.MAIN, TodoType.fromNameOrDefault(""))
        assertEquals(TodoType.MAIN, TodoType.fromNameOrDefault("UNKNOWN"))
    }
}
