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

    // --- toggleCompleted (비반복) ---

    @Test
    fun `toggleCompleted는 비반복 항목을 완료로 토글하고 알림을 취소한다`() = runTest {
        val item = makeItem("할 일", status = TodoStatus.NOT_STARTED, firestoreId = "id-1")
        fakeRepository.setItems(listOf(item))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        viewModel.toggleCompleted(item)
        advanceUntilIdle()

        val updated = fakeRepository.lastUpdatedItem
        assertEquals(true, updated?.isCompleted)
        assertEquals(1, fakeScheduler.cancelled.size)
        assertTrue(fakeScheduler.scheduled.isEmpty())
    }

    @Test
    fun `toggleCompleted는 완료된 비반복 항목을 다시 미완료로 되돌리고 마감일시·리마인더가 있으면 알림을 재예약한다`() = runTest {
        val item = makeItem(
            "할 일", status = TodoStatus.DONE, firestoreId = "id-1",
            dueAt = Instant.parse("2024-01-01T00:00:00Z"), reminderOffsetMinutes = 10,
        )
        fakeRepository.setItems(listOf(item))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        viewModel.toggleCompleted(item)
        advanceUntilIdle()

        val updated = fakeRepository.lastUpdatedItem
        assertEquals(false, updated?.isCompleted)
        assertEquals(TodoStatus.NOT_STARTED, updated?.status)
        assertNull(updated?.completedAt)
        assertEquals(1, fakeScheduler.scheduled.size)
        assertEquals(1, fakeScheduler.cancelled.size)
    }

    @Test
    fun `toggleCompleted는 마감일시나 리마인더 오프셋이 없으면 알림을 재예약하지 않는다`() = runTest {
        val item = makeItem("할 일", status = TodoStatus.DONE, firestoreId = "id-1")
        fakeRepository.setItems(listOf(item))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        viewModel.toggleCompleted(item)
        advanceUntilIdle()

        assertTrue(fakeScheduler.scheduled.isEmpty())
        assertEquals(1, fakeScheduler.cancelled.size)
    }

    // --- toggleCompleted (반복) ---

    @Test
    fun `toggleCompleted는 반복 항목을 다음 회차로 전진시키고 이전 알림을 취소한 뒤 재예약한다`() = runTest {
        val rule = RecurrenceRule(frequency = RecurrenceFrequency.DAILY)
        val dueAt = Instant.parse("2024-01-01T00:00:00Z")
        val item = makeItem(
            "반복 할 일", status = TodoStatus.NOT_STARTED, firestoreId = "id-1", recurrence = rule,
            dueAt = dueAt, reminderOffsetMinutes = 10,
        )
        fakeRepository.setItems(listOf(item))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        viewModel.toggleCompleted(item)
        advanceUntilIdle()

        val updated = fakeRepository.lastUpdatedItem
        assertEquals(false, updated?.isCompleted)
        assertEquals(dueAt.plusSeconds(24 * 60 * 60), updated?.dueAt)
        assertEquals(listOf(dueAt), updated?.completionHistory)
        assertEquals(1, fakeScheduler.scheduled.size)
        assertEquals(1, fakeScheduler.cancelled.size)
    }

    @Test
    fun `toggleCompleted는 이미 종료된 반복 항목을 다시 탭하면 completionHistory를 중복 누적하지 않고 단순 토글한다`() = runTest {
        val rule = RecurrenceRule(frequency = RecurrenceFrequency.DAILY, endAt = Instant.parse("2024-01-01T00:00:00Z"))
        val lastDueAt = Instant.parse("2024-01-01T00:00:00Z")
        val history = listOf(Instant.parse("2023-12-31T00:00:00Z"))
        val endedItem = makeItem(
            "종료된 반복 할 일",
            status = TodoStatus.DONE,
            firestoreId = "id-1",
            recurrence = rule,
            dueAt = lastDueAt,
        ).copy(completionHistory = history, completedAt = lastDueAt)
        fakeRepository.setItems(listOf(endedItem))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        viewModel.toggleCompleted(endedItem)
        advanceUntilIdle()

        val updated = fakeRepository.lastUpdatedItem
        assertEquals(false, updated?.isCompleted)
        assertNull(updated?.completedAt)
        assertEquals(lastDueAt, updated?.dueAt)
        assertEquals(history, updated?.completionHistory)
    }

    @Test
    fun `toggleCompleted 저장 실패 시 uiState가 Error가 된다`() = runTest {
        val item = makeItem("할 일", firestoreId = "id-1")
        fakeRepository.setItems(listOf(item))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        fakeRepository.updateTodoItemError = RuntimeException("업데이트 실패")
        viewModel.toggleCompleted(item)
        advanceUntilIdle()

        val error = viewModel.uiState.value as TodoUiState.Error
        assertTrue(error.message.contains("할 일 상태 변경에 실패했습니다"))
    }

    // --- toggleInProgress ---

    @Test
    fun `toggleInProgress는 미진행 항목을 진행중으로 바꾼다`() = runTest {
        val item = makeItem("할 일", status = TodoStatus.NOT_STARTED, firestoreId = "id-1")
        fakeRepository.setItems(listOf(item))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        viewModel.toggleInProgress(item)
        advanceUntilIdle()

        assertEquals(TodoStatus.IN_PROGRESS, fakeRepository.lastUpdatedItem?.status)
    }

    @Test
    fun `toggleInProgress는 진행중 항목을 미진행으로 되돌린다`() = runTest {
        val item = makeItem("할 일", status = TodoStatus.IN_PROGRESS, firestoreId = "id-1")
        fakeRepository.setItems(listOf(item))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        viewModel.toggleInProgress(item)
        advanceUntilIdle()

        assertEquals(TodoStatus.NOT_STARTED, fakeRepository.lastUpdatedItem?.status)
    }

    @Test
    fun `toggleInProgress는 완료 항목에는 아무 것도 하지 않는다`() = runTest {
        val item = makeItem("할 일", status = TodoStatus.DONE, firestoreId = "id-1")
        fakeRepository.setItems(listOf(item))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        viewModel.toggleInProgress(item)
        advanceUntilIdle()

        assertNull(fakeRepository.lastUpdatedItem)
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
}
