package com.jkapp.todo

import com.jkapp.auth.FakeAuthRepository
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodoViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeTodoFirestoreRepository
    private lateinit var fakeAuth: FakeAuthRepository
    private lateinit var fakeScheduler: FakeTodoReminderScheduler
    private lateinit var viewModel: TodoViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeTodoFirestoreRepository()
        fakeAuth = FakeAuthRepository(initialLoggedIn = false)
        fakeScheduler = FakeTodoReminderScheduler()
        viewModel = TodoViewModel(repository = fakeRepository, reminderScheduler = fakeScheduler, authRepository = fakeAuth)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- 인증 상태 ---

    @Test
    fun `로그아웃 상태에서 uiState는 Loading이다`() = runTest {
        advanceUntilIdle()
        assertEquals(TodoUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `로그인하면 데이터 수집이 시작되어 Success 상태가 된다`() = runTest {
        val items = listOf(makeItem("할 일 1"))
        fakeRepository.setItems(items)

        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        val state = viewModel.uiState.value as TodoUiState.Success
        assertEquals(items, state.items)
    }

    @Test
    fun `로그인 후 로그아웃하면 uiState가 Loading으로 돌아온다`() = runTest {
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        fakeAuth.setLoggedIn(false)
        advanceUntilIdle()

        assertEquals(TodoUiState.Loading, viewModel.uiState.value)
    }

    // --- 필터 상태 ---

    @Test
    fun `초기 statusFilter는 TODAY이다`() = runTest {
        assertEquals(TodoStatusFilter.TODAY, viewModel.statusFilter.value)
    }

    @Test
    fun `setStatusFilter 호출 시 statusFilter가 변경된다`() = runTest {
        viewModel.setStatusFilter(TodoStatusFilter.COMPLETED)
        assertEquals(TodoStatusFilter.COMPLETED, viewModel.statusFilter.value)
    }

    @Test
    fun `toggleAssigneeFilter 호출 시 assigneeFilter에 추가되고 다시 호출하면 제거된다`() = runTest {
        viewModel.toggleAssigneeFilter(TodoAssignee.JEON_JIHOON)
        assertTrue(TodoAssignee.JEON_JIHOON in viewModel.assigneeFilter.value)

        viewModel.toggleAssigneeFilter(TodoAssignee.JEON_JIHOON)
        assertTrue(TodoAssignee.JEON_JIHOON !in viewModel.assigneeFilter.value)
    }

    @Test
    fun `clearAssigneeFilter 호출 시 assigneeFilter가 비워진다`() = runTest {
        viewModel.toggleAssigneeFilter(TodoAssignee.JEON_JIHOON)
        viewModel.clearAssigneeFilter()
        assertTrue(viewModel.assigneeFilter.value.isEmpty())
    }

    // --- visibleItems ---

    @Test
    fun `visibleItems는 ALL 필터에서 완료되지 않은 항목만 노출한다`() = runTest {
        val active = makeItem("진행중", status = TodoStatus.NOT_STARTED)
        val completed = makeItem("완료됨", status = TodoStatus.DONE)
        fakeRepository.setItems(listOf(active, completed))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        viewModel.setStatusFilter(TodoStatusFilter.ALL)
        advanceUntilIdle()

        assertEquals(listOf(active), viewModel.visibleItems.value)
    }

    @Test
    fun `visibleItems는 담당자 필터를 반영한다`() = runTest {
        val jeon = makeItem("전지훈 할일", assignee = TodoAssignee.JEON_JIHOON)
        val kwon = makeItem("권유경 할일", assignee = TodoAssignee.KWON_YUKYEONG)
        fakeRepository.setItems(listOf(jeon, kwon))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        viewModel.setStatusFilter(TodoStatusFilter.ALL)
        viewModel.toggleAssigneeFilter(TodoAssignee.JEON_JIHOON)
        advanceUntilIdle()

        assertEquals(listOf(jeon), viewModel.visibleItems.value)
    }

    // --- advanceStatus (상태 사이클: 미진행 -> 진행중 -> 완료 -> 미진행) ---

    @Test
    fun `advanceStatus는 미진행 항목을 진행중으로 바꾼다`() = runTest {
        val item = makeItem("할 일", status = TodoStatus.NOT_STARTED, firestoreId = "id-1")
        fakeRepository.setItems(listOf(item))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        viewModel.advanceStatus(item)
        advanceUntilIdle()

        assertEquals(TodoStatus.IN_PROGRESS, fakeRepository.lastUpdatedItem?.status)
        assertTrue(fakeScheduler.scheduled.isEmpty())
    }

    @Test
    fun `advanceStatus는 진행중 비반복 항목을 완료로 바꾸고 알림을 취소한다`() = runTest {
        val item = makeItem("할 일", status = TodoStatus.IN_PROGRESS, firestoreId = "id-1")
        fakeRepository.setItems(listOf(item))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        viewModel.advanceStatus(item)
        advanceUntilIdle()

        // 완료 확정은 completeTodoItem 경로를 탄다(cascade·무알림 강제).
        assertEquals("id-1", fakeRepository.lastCompletedItemId)
        val stored = fakeRepository.itemById("id-1")
        assertEquals(TodoStatus.DONE, stored?.status)
        assertEquals(true, stored?.isCompleted)
        assertEquals(1, fakeScheduler.cancelled.size)
        assertTrue(fakeScheduler.scheduled.isEmpty())
    }

    @Test
    fun `advanceStatus는 진행중을 완료 처리하면 마감·리마인더가 있어도 재예약하지 않는다`() = runTest {
        val item = makeItem(
            "할 일", status = TodoStatus.IN_PROGRESS, firestoreId = "id-1",
            dueAt = Instant.parse("2024-01-01T00:00:00Z"), reminderOffsetMinutes = 10,
        )
        fakeRepository.setItems(listOf(item))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        viewModel.advanceStatus(item)
        advanceUntilIdle()

        assertEquals("id-1", fakeRepository.lastCompletedItemId)
        assertEquals(TodoStatus.DONE, fakeRepository.itemById("id-1")?.status)
        assertTrue(fakeScheduler.scheduled.isEmpty())
        assertEquals(1, fakeScheduler.cancelled.size)
    }

    @Test
    fun `advanceStatus는 진행중 반복 항목을 다음 회차로 전진시키고 이전 알림을 취소한 뒤 재예약한다`() = runTest {
        val rule = RecurrenceRule(frequency = RecurrenceFrequency.DAILY)
        val dueAt = Instant.parse("2024-01-01T00:00:00Z")
        val item = makeItem(
            "반복 할 일", status = TodoStatus.IN_PROGRESS, firestoreId = "id-1", recurrence = rule,
            dueAt = dueAt, reminderOffsetMinutes = 10,
        )
        fakeRepository.setItems(listOf(item))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        viewModel.advanceStatus(item)
        advanceUntilIdle()

        // 반복 완료도 completeTodoItem 경로를 탄다.
        assertEquals("id-1", fakeRepository.lastCompletedItemId)
        val stored = fakeRepository.itemById("id-1")
        assertEquals(false, stored?.isCompleted)
        assertEquals(TodoStatus.NOT_STARTED, stored?.status)
        assertEquals(dueAt.plusSeconds(24 * 60 * 60), stored?.dueAt)
        assertEquals(listOf(dueAt), stored?.completionHistory)
        assertEquals(1, fakeScheduler.scheduled.size)
        assertEquals(1, fakeScheduler.cancelled.size)
    }

    @Test
    fun `advanceStatus는 완료 항목을 미진행으로 되돌리고 마감·리마인더가 있으면 재예약한다`() = runTest {
        val item = makeItem(
            "할 일", status = TodoStatus.DONE, firestoreId = "id-1",
            dueAt = Instant.parse("2024-01-01T00:00:00Z"), reminderOffsetMinutes = 10,
        )
        fakeRepository.setItems(listOf(item))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        viewModel.advanceStatus(item)
        advanceUntilIdle()

        val updated = fakeRepository.lastUpdatedItem
        assertEquals(TodoStatus.NOT_STARTED, updated?.status)
        assertNull(updated?.completedAt)
        assertEquals(1, fakeScheduler.scheduled.size)
        assertEquals(1, fakeScheduler.cancelled.size)
    }

    @Test
    fun `advanceStatus 저장 실패 시 uiState가 Error가 된다`() = runTest {
        val item = makeItem("할 일", firestoreId = "id-1")
        fakeRepository.setItems(listOf(item))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        fakeRepository.updateTodoItemError = RuntimeException("업데이트 실패")
        viewModel.advanceStatus(item)
        advanceUntilIdle()

        val error = viewModel.uiState.value as TodoUiState.Error
        assertTrue(error.message.contains("할 일 상태 변경에 실패했습니다"))
    }

    // --- addTodoItem ---

    @Test
    fun `addTodoItem은 저장 성공 시 saveCompleted를 true로 만들고 마감일시·리마인더가 있으면 알림을 예약한다`() = runTest {
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        val newItem = makeItem(
            "새 할 일", status = TodoStatus.NOT_STARTED,
            dueAt = Instant.parse("2024-01-01T00:00:00Z"), reminderOffsetMinutes = 10,
        )
        viewModel.addTodoItem(newItem)
        advanceUntilIdle()

        assertEquals(newItem, fakeRepository.lastAddedItem)
        assertTrue(viewModel.saveCompleted.value)
        assertEquals(1, fakeScheduler.scheduled.size)
    }

    @Test
    fun `addTodoItem은 완료된 항목이면 알림을 예약하지 않는다`() = runTest {
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        viewModel.addTodoItem(
            makeItem(
                "완료된 할 일", status = TodoStatus.DONE,
                dueAt = Instant.parse("2024-01-01T00:00:00Z"), reminderOffsetMinutes = 10,
            )
        )
        advanceUntilIdle()

        assertTrue(fakeScheduler.scheduled.isEmpty())
    }

    @Test
    fun `addTodoItem은 마감일시나 리마인더 오프셋이 없으면 알림을 예약하지 않는다`() = runTest {
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        viewModel.addTodoItem(makeItem("새 할 일", status = TodoStatus.NOT_STARTED))
        advanceUntilIdle()

        assertTrue(fakeScheduler.scheduled.isEmpty())
    }

    @Test
    fun `addTodoItem 저장 실패 시 uiState가 Error가 되고 saveCompleted는 false로 유지된다`() = runTest {
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        fakeRepository.addTodoItemError = RuntimeException("추가 실패")
        viewModel.addTodoItem(makeItem("새 할 일"))
        advanceUntilIdle()

        val error = viewModel.uiState.value as TodoUiState.Error
        assertTrue(error.message.contains("할 일 저장에 실패했습니다"))
        assertTrue(!viewModel.saveCompleted.value)
    }

    // --- updateTodoItem ---

    @Test
    fun `updateTodoItem은 저장 성공 시 이전 알림을 취소하고 saveCompleted를 true로 만든다`() = runTest {
        val item = makeItem(
            "할 일", firestoreId = "id-1",
            dueAt = Instant.parse("2024-01-01T00:00:00Z"), reminderOffsetMinutes = 10,
        )
        fakeRepository.setItems(listOf(item))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        val updated = item.copy(title = "수정된 할 일")
        viewModel.updateTodoItem(updated)
        advanceUntilIdle()

        assertEquals(updated, fakeRepository.lastUpdatedItem)
        assertEquals(1, fakeScheduler.cancelled.size)
        assertEquals(1, fakeScheduler.scheduled.size)
        assertTrue(viewModel.saveCompleted.value)
    }

    @Test
    fun `consumeSaveCompleted 호출 시 saveCompleted가 false로 초기화된다`() = runTest {
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        viewModel.addTodoItem(makeItem("새 할 일"))
        advanceUntilIdle()
        assertTrue(viewModel.saveCompleted.value)

        viewModel.consumeSaveCompleted()
        assertTrue(!viewModel.saveCompleted.value)
    }

    // --- deleteTodoItem ---

    @Test
    fun `deleteTodoItem은 항목을 삭제하고 알림을 취소한다`() = runTest {
        val item = makeItem("할 일", firestoreId = "id-1")
        fakeRepository.setItems(listOf(item))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        viewModel.deleteTodoItem(item)
        advanceUntilIdle()

        assertEquals("id-1", fakeRepository.lastDeletedItemId)
        assertEquals(1, fakeScheduler.cancelled.size)
    }

    // --- 자식(SUB) 항목 (이슈 #88) ---

    @Test
    fun `visibleItems는 SUB 항목을 목록에 노출하지 않는다`() = runTest {
        val parent = makeItem("부모", status = TodoStatus.NOT_STARTED, firestoreId = "p-1")
        val sub = makeSub("자식", firestoreId = "s-1", parentId = "p-1")
        fakeRepository.setItems(listOf(parent, sub))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        viewModel.setStatusFilter(TodoStatusFilter.ALL)
        advanceUntilIdle()

        assertEquals(listOf(parent), viewModel.visibleItems.value)
    }

    @Test
    fun `addSubTodoItem은 repository의 자식 추가를 호출한다`() = runTest {
        val parent = makeItem("부모", firestoreId = "p-1")
        fakeRepository.setItems(listOf(parent))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        viewModel.addSubTodoItem("p-1", makeItem("새 자식"))
        advanceUntilIdle()

        assertEquals("p-1", fakeRepository.lastAddedSubParentId)
        assertEquals("새 자식", fakeRepository.lastAddedSubItem?.title)
        assertTrue(viewModel.saveCompleted.value)
    }

    @Test
    fun `advanceSubStatus는 자식을 진행중으로 바꾸고 미진행 부모를 진행중으로 전이시킨다`() = runTest {
        val parent = makeItem("부모", status = TodoStatus.NOT_STARTED, firestoreId = "p-1")
        val sub = makeSub(
            "자식", status = TodoStatus.NOT_STARTED, firestoreId = "s-1", parentId = "p-1",
        )
        fakeRepository.setItems(listOf(parent, sub))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        viewModel.advanceSubStatus(sub)
        advanceUntilIdle()

        // 마지막 업데이트는 부모 전이(IN_PROGRESS).
        val updatedParent = fakeRepository.getTodoItemOnce("p-1")
        assertEquals(TodoStatus.IN_PROGRESS, updatedParent?.status)
        val updatedSub = fakeRepository.getTodoItemOnce("s-1")
        assertEquals(TodoStatus.IN_PROGRESS, updatedSub?.status)
    }

    @Test
    fun `advanceSubStatus는 이미 진행중인 부모를 완료로 전이시키지 않는다`() = runTest {
        val parent = makeItem("부모", status = TodoStatus.IN_PROGRESS, firestoreId = "p-1")
        val sub = makeSub(
            "자식", status = TodoStatus.IN_PROGRESS, firestoreId = "s-1", parentId = "p-1",
        )
        fakeRepository.setItems(listOf(parent, sub))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        viewModel.advanceSubStatus(sub)
        advanceUntilIdle()

        // 자식은 DONE이 되지만 부모는 자동완료되지 않는다(IN_PROGRESS 유지).
        val updatedSub = fakeRepository.getTodoItemOnce("s-1")
        assertEquals(TodoStatus.DONE, updatedSub?.status)
        val updatedParent = fakeRepository.getTodoItemOnce("p-1")
        assertEquals(TodoStatus.IN_PROGRESS, updatedParent?.status)
    }

    @Test
    fun `deleteTodoItems는 부모와 자식을 함께 삭제한다(cascade)`() = runTest {
        val parent = makeItem("부모", firestoreId = "p-1")
        val sub = makeSub("자식", firestoreId = "s-1", parentId = "p-1")
        val other = makeItem("다른 부모", firestoreId = "p-2")
        fakeRepository.setItems(listOf(parent, sub, other))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        viewModel.deleteTodoItems(listOf(parent))
        advanceUntilIdle()

        assertEquals(listOf("p-1"), fakeRepository.lastDeletedItemIds)
        assertNull(fakeRepository.getTodoItemOnce("p-1"))
        assertNull(fakeRepository.getTodoItemOnce("s-1"))
        assertEquals("p-2", fakeRepository.getTodoItemOnce("p-2")?.firestoreId)
    }

    @Test
    fun `반복 부모 완료 시 자식이 있으면 pendingSubClone이 설정된다`() = runTest {
        val rule = RecurrenceRule(frequency = RecurrenceFrequency.DAILY)
        val dueAt = Instant.parse("2024-01-01T00:00:00Z")
        val parent = makeItem(
            "반복 부모", status = TodoStatus.IN_PROGRESS, firestoreId = "p-1",
            recurrence = rule, dueAt = dueAt,
        )
        val sub = makeSub("자식", firestoreId = "s-1", parentId = "p-1")
        fakeRepository.setItems(listOf(parent, sub))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        viewModel.advanceStatus(parent)
        advanceUntilIdle()

        val pending = viewModel.pendingSubClone.value
        assertEquals("p-1", pending?.first)
        assertEquals(listOf(sub), pending?.second)
    }

    @Test
    fun `반복 부모 완료 시 자식이 없으면 pendingSubClone은 설정되지 않는다`() = runTest {
        val rule = RecurrenceRule(frequency = RecurrenceFrequency.DAILY)
        val dueAt = Instant.parse("2024-01-01T00:00:00Z")
        val parent = makeItem(
            "반복 부모", status = TodoStatus.IN_PROGRESS, firestoreId = "p-1",
            recurrence = rule, dueAt = dueAt,
        )
        fakeRepository.setItems(listOf(parent))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        viewModel.advanceStatus(parent)
        advanceUntilIdle()

        assertNull(viewModel.pendingSubClone.value)
    }

    @Test
    fun `cloneSubsToNextOccurrence는 자식을 NOT_STARTED로 새 부모에 복제한다`() = runTest {
        val newParent = makeItem("새 회차 부모", firestoreId = "p-1")
        fakeRepository.setItems(listOf(newParent))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        val subToClone = makeSub("복제 자식", status = TodoStatus.DONE, parentId = "old")
        viewModel.cloneSubsToNextOccurrence("p-1", listOf(subToClone))
        advanceUntilIdle()

        assertEquals("p-1", fakeRepository.lastAddedSubParentId)
        assertEquals("복제 자식", fakeRepository.lastAddedSubItem?.title)
        assertEquals(TodoStatus.NOT_STARTED, fakeRepository.lastAddedSubItem?.status)
    }

    @Test
    fun `cloneSubsToNextOccurrence는 무알림(notify=false)으로 복제해 push를 만들지 않는다`() = runTest {
        val newParent = makeItem("새 회차 부모", firestoreId = "p-1")
        fakeRepository.setItems(listOf(newParent))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        val subs = listOf(
            makeSub("자식1", parentId = "old"),
            makeSub("자식2", parentId = "old"),
        )
        viewModel.cloneSubsToNextOccurrence("p-1", subs)
        advanceUntilIdle()

        // 복제 자식이 여러 개여도 push는 0건이어야 한다(≤1 push 원칙).
        assertEquals(false, fakeRepository.lastAddSubNotify)
        assertEquals(0, fakeRepository.subNotifyPushCount)
    }

    // --- 부모 DONE cascade (advanceStatus 경로, 이슈 #88 CRITICAL 회귀) ---

    @Test
    fun `advanceStatus로 부모를 완료하면 자식도 DONE으로 cascade되고 push는 발생하지 않는다`() = runTest {
        val parent = makeItem("부모", status = TodoStatus.IN_PROGRESS, firestoreId = "p-1")
        val sub1 = makeSub(
            "자식1", status = TodoStatus.NOT_STARTED, firestoreId = "s-1", parentId = "p-1",
        )
        val sub2 = makeSub(
            "자식2", status = TodoStatus.IN_PROGRESS, firestoreId = "s-2", parentId = "p-1",
        )
        fakeRepository.setItems(listOf(parent, sub1, sub2))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        viewModel.advanceStatus(parent)
        advanceUntilIdle()

        // 완료는 completeTodoItem 경로(cascade+무알림)를 탄다.
        assertEquals("p-1", fakeRepository.lastCompletedItemId)
        assertEquals(TodoStatus.DONE, fakeRepository.itemById("p-1")?.status)
        assertEquals(TodoStatus.DONE, fakeRepository.itemById("s-1")?.status)
        assertEquals(TodoStatus.DONE, fakeRepository.itemById("s-2")?.status)
        // 부모 완료·자식 cascade 모두 무알림이므로 자식 생성 push는 0건.
        assertEquals(0, fakeRepository.subNotifyPushCount)
    }

    @Test
    fun `advanceSubStatus 부모 전이 실패는 uiState를 Error로 덮지 않는다`() = runTest {
        val parent = makeItem("부모", status = TodoStatus.NOT_STARTED, firestoreId = "p-1")
        val sub = makeSub(
            "자식", status = TodoStatus.NOT_STARTED, firestoreId = "s-1", parentId = "p-1",
        )
        fakeRepository.setItems(listOf(parent, sub))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        // 자식 변경(주 작업)은 성공, 이어지는 부모 전이(부수 작업)만 실패시킨다.
        fakeRepository.failNextUpdateAfter(1)
        viewModel.advanceSubStatus(sub)
        advanceUntilIdle()

        // 부수 작업 실패는 삼켜지고 uiState는 Success를 유지한다.
        assertTrue(viewModel.uiState.value is TodoUiState.Success)
    }

    // --- helpers ---

    private fun makeItem(
        title: String,
        status: TodoStatus = TodoStatus.NOT_STARTED,
        assignee: TodoAssignee = TodoAssignee.SHARED,
        firestoreId: String? = null,
        recurrence: RecurrenceRule? = null,
        dueAt: Instant? = null,
        reminderOffsetMinutes: Int? = null,
    ) = TodoItem(
        firestoreId = firestoreId,
        title = title,
        status = status,
        assignee = assignee,
        recurrence = recurrence,
        dueAt = dueAt,
        reminderOffsetMinutes = reminderOffsetMinutes,
    )

    private fun makeSub(
        title: String,
        parentId: String,
        status: TodoStatus = TodoStatus.NOT_STARTED,
        firestoreId: String? = null,
    ) = TodoItem(
        firestoreId = firestoreId,
        type = TodoType.SUB,
        mainTodoId = parentId,
        title = title,
        status = status,
    )
}
