package com.jkapp.todo

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoFilterTest {

    private val zone = ZoneId.of("Asia/Seoul")
    // 2024-01-15 12:00 KST 기준 "오늘" 판정을 검증한다.
    private val now = Instant.parse("2024-01-15T03:00:00Z")

    private fun item(
        title: String = "title",
        status: TodoStatus = TodoStatus.NOT_STARTED,
        assignee: TodoAssignee = TodoAssignee.SHARED,
        priority: TodoPriority = TodoPriority.NONE,
        dueAt: Instant? = null,
        recurrence: RecurrenceRule? = null,
        createdAt: Instant? = null,
    ) = TodoItem(
        title = title,
        status = status,
        assignee = assignee,
        priority = priority,
        dueAt = dueAt,
        recurrence = recurrence,
        createdAt = createdAt,
    )

    // region filterByStatus

    @Test
    fun `filterByStatus TODAY는 마감이 오늘이거나 미설정이고 완료가 아닌 항목을 남긴다`() {
        val todayNotStarted = item(title = "today", dueAt = Instant.parse("2024-01-15T10:00:00Z"))
        val todayInProgress = item(title = "today-inprogress", status = TodoStatus.IN_PROGRESS, dueAt = Instant.parse("2024-01-15T10:00:00Z"))
        val noDueInProgress = item(title = "no-due", status = TodoStatus.IN_PROGRESS, dueAt = null)
        val todayDone = item(title = "today-done", status = TodoStatus.DONE, dueAt = Instant.parse("2024-01-15T10:00:00Z"))
        val tomorrow = item(title = "tomorrow", dueAt = Instant.parse("2024-01-16T10:00:00Z"))
        val items = listOf(todayNotStarted, todayInProgress, noDueInProgress, todayDone, tomorrow)

        val result = TodoViewModel.filterByStatus(items, TodoStatusFilter.TODAY, now, zone)

        // 완료(todayDone)와 오늘이 아닌 마감(tomorrow)은 제외, 미설정 마감은 포함.
        assertEquals(listOf(todayNotStarted, todayInProgress, noDueInProgress), result)
    }

    @Test
    fun `filterByStatus TODAY는 자정 경계를 zone 기준으로 판정한다`() {
        // 2024-01-15T14:30Z == 2024-01-15 23:30 KST -> 오늘. 2024-01-15T15:30Z == 2024-01-16 00:30 KST -> 내일.
        val lateToday = item(title = "late-today", dueAt = Instant.parse("2024-01-15T14:30:00Z"))
        val earlyTomorrow = item(title = "early-tomorrow", dueAt = Instant.parse("2024-01-15T15:30:00Z"))
        val result = TodoViewModel.filterByStatus(listOf(lateToday, earlyTomorrow), TodoStatusFilter.TODAY, now, zone)
        assertEquals(listOf(lateToday), result)
    }

    @Test
    fun `filterByStatus ALL은 완료되지 않은 항목만 남긴다`() {
        val notStarted = item(status = TodoStatus.NOT_STARTED)
        val inProgress = item(status = TodoStatus.IN_PROGRESS)
        val done = item(status = TodoStatus.DONE)
        val result = TodoViewModel.filterByStatus(listOf(notStarted, inProgress, done), TodoStatusFilter.ALL, now, zone)
        assertEquals(listOf(notStarted, inProgress), result)
    }

    @Test
    fun `filterByStatus RECURRING은 반복 설정된 항목만 남긴다`() {
        val recurring = item(recurrence = RecurrenceRule(frequency = RecurrenceFrequency.DAILY))
        val once = item(recurrence = null)
        val result = TodoViewModel.filterByStatus(listOf(recurring, once), TodoStatusFilter.RECURRING, now, zone)
        assertEquals(listOf(recurring), result)
    }

    @Test
    fun `filterByStatus COMPLETED는 반복이 없고 완료된 항목만 남긴다`() {
        val completedOnce = item(status = TodoStatus.DONE, recurrence = null)
        val completedRecurring = item(status = TodoStatus.DONE, recurrence = RecurrenceRule(frequency = RecurrenceFrequency.DAILY))
        val activeOnce = item(status = TodoStatus.NOT_STARTED, recurrence = null)
        val result = TodoViewModel.filterByStatus(
            listOf(completedOnce, completedRecurring, activeOnce), TodoStatusFilter.COMPLETED, now, zone,
        )
        assertEquals(listOf(completedOnce), result)
    }

    // endregion

    // region filterByAssignee

    @Test
    fun `filterByAssignee returns all items when assignees is empty`() {
        val items = listOf(item(assignee = TodoAssignee.SHARED), item(assignee = TodoAssignee.JEON_JIHOON))
        assertEquals(items, TodoViewModel.filterByAssignee(items, emptySet()))
    }

    @Test
    fun `filterByAssignee keeps only exact matching assignee`() {
        val jeon = item(assignee = TodoAssignee.JEON_JIHOON)
        val shared = item(assignee = TodoAssignee.SHARED)
        val kwon = item(assignee = TodoAssignee.KWON_YUKYEONG)
        val result = TodoViewModel.filterByAssignee(listOf(jeon, shared, kwon), setOf(TodoAssignee.JEON_JIHOON))
        assertEquals(listOf(jeon), result)
    }

    // endregion

    // region sortItems

    @Test
    fun `sortItems는 마감시간 가까운 순으로 정렬하고 마감없음을 마지막에 둔다`() {
        val earlier = item(title = "earlier", dueAt = Instant.parse("2024-01-01T00:00:00Z"))
        val later = item(title = "later", dueAt = Instant.parse("2024-02-01T00:00:00Z"))
        val noDue = item(title = "no-due", dueAt = null)
        val result = TodoViewModel.sortItems(listOf(later, noDue, earlier))
        assertEquals(listOf("earlier", "later", "no-due"), result.map { it.title })
    }

    @Test
    fun `sortItems는 마감시간이 같으면 상태 순(미진행-진행중-완료)으로 정렬한다`() {
        val due = Instant.parse("2024-01-01T00:00:00Z")
        val done = item(title = "done", status = TodoStatus.DONE, dueAt = due)
        val notStarted = item(title = "not-started", status = TodoStatus.NOT_STARTED, dueAt = due)
        val inProgress = item(title = "in-progress", status = TodoStatus.IN_PROGRESS, dueAt = due)
        val result = TodoViewModel.sortItems(listOf(done, inProgress, notStarted))
        assertEquals(listOf("not-started", "in-progress", "done"), result.map { it.title })
    }

    // endregion

    // region filterAndSort

    @Test
    fun `filterAndSort는 상태-담당자 필터를 적용한 뒤 정렬한다`() {
        val target = item(
            title = "target",
            status = TodoStatus.NOT_STARTED,
            assignee = TodoAssignee.JEON_JIHOON,
            dueAt = Instant.parse("2024-01-01T00:00:00Z"),
        )
        val wrongAssignee = item(assignee = TodoAssignee.KWON_YUKYEONG)
        val done = item(status = TodoStatus.DONE, assignee = TodoAssignee.JEON_JIHOON)
        val items = listOf(wrongAssignee, done, target)

        val result = TodoViewModel.filterAndSort(
            items,
            statusFilter = TodoStatusFilter.ALL,
            assigneeFilter = setOf(TodoAssignee.JEON_JIHOON),
            now = now,
            zone = ZoneOffset.UTC,
        )

        assertEquals(listOf(target), result)
        assertTrue(result.isNotEmpty())
    }

    // endregion
}
