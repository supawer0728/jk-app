package com.jkapp.todo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodoStatusMigrationTest {

    @Test
    fun `legacy isCompleted true는 DONE으로 매핑된다`() {
        assertEquals(TodoStatus.DONE, todoStatusFromLegacyCompleted(true))
    }

    @Test
    fun `legacy isCompleted false는 NOT_STARTED로 매핑된다`() {
        assertEquals(TodoStatus.NOT_STARTED, todoStatusFromLegacyCompleted(false))
    }

    @Test
    fun `toTodoStatusOrNull은 유효한 이름을 파싱하고 그 외에는 null이다`() {
        assertEquals(TodoStatus.IN_PROGRESS, "IN_PROGRESS".toTodoStatusOrNull())
        assertNull("UNKNOWN".toTodoStatusOrNull())
    }

    @Test
    fun `isCompleted는 status가 DONE일 때만 true인 파생값이다`() {
        assertEquals(false, TodoItem(title = "a", status = TodoStatus.NOT_STARTED).isCompleted)
        assertEquals(false, TodoItem(title = "a", status = TodoStatus.IN_PROGRESS).isCompleted)
        assertEquals(true, TodoItem(title = "a", status = TodoStatus.DONE).isCompleted)
    }

    @Test
    fun `TodoAssignee fromNameOrDefault는 알 수 없는 값이면 공동을 반환한다`() {
        assertEquals(TodoAssignee.JEON_JIHOON, TodoAssignee.fromNameOrDefault("JEON_JIHOON"))
        assertEquals(TodoAssignee.SHARED, TodoAssignee.fromNameOrDefault(null))
        assertEquals(TodoAssignee.SHARED, TodoAssignee.fromNameOrDefault("NOBODY"))
    }

    @Test
    fun `TodoAssignee 공동은 두 사용자 이메일을 모두 포함한다`() {
        assertEquals(
            listOf(TodoAssignee.EMAIL_JEON_JIHOON, TodoAssignee.EMAIL_KWON_YUKYEONG),
            TodoAssignee.SHARED.emails,
        )
        assertEquals(listOf(TodoAssignee.EMAIL_KWON_YUKYEONG), TodoAssignee.KWON_YUKYEONG.emails)
        assertEquals(listOf(TodoAssignee.EMAIL_JEON_JIHOON), TodoAssignee.JEON_JIHOON.emails)
    }
}
