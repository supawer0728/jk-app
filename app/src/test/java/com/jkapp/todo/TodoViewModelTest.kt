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
        val categories = listOf(makeCategory("업무"))
        fakeRepository.setItems(items)
        fakeRepository.setCategories(categories)

        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        val state = viewModel.uiState.value as TodoUiState.Success
        assertEquals(items, state.items)
        assertEquals(categories, state.categories)
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
    fun `초기 statusFilter는 ACTIVE이다`() = runTest {
        assertEquals(TodoStatusFilter.ACTIVE, viewModel.statusFilter.value)
    }

    @Test
    fun `setStatusFilter 호출 시 statusFilter가 변경된다`() = runTest {
        viewModel.setStatusFilter(TodoStatusFilter.COMPLETED)
        assertEquals(TodoStatusFilter.COMPLETED, viewModel.statusFilter.value)
    }

    @Test
    fun `toggleCategoryFilter 호출 시 categoryFilter에 추가되고 다시 호출하면 제거된다`() = runTest {
        viewModel.toggleCategoryFilter("work")
        assertTrue("work" in viewModel.categoryFilter.value)

        viewModel.toggleCategoryFilter("work")
        assertTrue("work" !in viewModel.categoryFilter.value)
    }

    @Test
    fun `clearCategoryFilter 호출 시 categoryFilter가 비워진다`() = runTest {
        viewModel.toggleCategoryFilter("work")
        viewModel.clearCategoryFilter()
        assertTrue(viewModel.categoryFilter.value.isEmpty())
    }

    @Test
    fun `toggleTagFilter 호출 시 tagFilter에 추가되고 다시 호출하면 제거된다`() = runTest {
        viewModel.toggleTagFilter("urgent")
        assertTrue("urgent" in viewModel.tagFilter.value)

        viewModel.toggleTagFilter("urgent")
        assertTrue("urgent" !in viewModel.tagFilter.value)
    }

    @Test
    fun `clearTagFilter 호출 시 tagFilter가 비워진다`() = runTest {
        viewModel.toggleTagFilter("urgent")
        viewModel.clearTagFilter()
        assertTrue(viewModel.tagFilter.value.isEmpty())
    }

    @Test
    fun `setSortOption 호출 시 sortOption이 변경된다`() = runTest {
        viewModel.setSortOption(TodoSortOption.PRIORITY)
        assertEquals(TodoSortOption.PRIORITY, viewModel.sortOption.value)
    }

    // --- visibleItems ---

    @Test
    fun `visibleItems는 기본적으로 ACTIVE 상태만 노출한다`() = runTest {
        val active = makeItem("진행중", isCompleted = false)
        val completed = makeItem("완료됨", isCompleted = true)
        fakeRepository.setItems(listOf(active, completed))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        assertEquals(listOf(active), viewModel.visibleItems.value)
    }

    @Test
    fun `visibleItems는 카테고리 필터를 반영한다`() = runTest {
        val work = makeItem("업무", categoryId = "work")
        val home = makeItem("가사", categoryId = "home")
        fakeRepository.setItems(listOf(work, home))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        viewModel.toggleCategoryFilter("work")
        advanceUntilIdle()

        assertEquals(listOf(work), viewModel.visibleItems.value)
    }

    @Test
    fun `visibleItems는 태그 필터를 반영한다`() = runTest {
        val urgent = makeItem("긴급", tags = listOf("urgent"))
        val normal = makeItem("일반", tags = listOf("home"))
        fakeRepository.setItems(listOf(urgent, normal))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        viewModel.toggleTagFilter("urgent")
        advanceUntilIdle()

        assertEquals(listOf(urgent), viewModel.visibleItems.value)
    }

    // --- toggleCompleted (비반복) ---

    @Test
    fun `toggleCompleted는 비반복 항목을 완료로 토글하고 알림을 취소한다`() = runTest {
        val item = makeItem("할 일", isCompleted = false, firestoreId = "id-1")
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
    fun `toggleCompleted는 완료된 비반복 항목을 다시 미완료로 되돌리고 알림을 재예약한다`() = runTest {
        val item = makeItem("할 일", isCompleted = true, firestoreId = "id-1")
        fakeRepository.setItems(listOf(item))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        viewModel.toggleCompleted(item)
        advanceUntilIdle()

        val updated = fakeRepository.lastUpdatedItem
        assertEquals(false, updated?.isCompleted)
        assertNull(updated?.completedAt)
        assertEquals(1, fakeScheduler.scheduled.size)
        assertEquals(1, fakeScheduler.cancelled.size)
    }

    // --- toggleCompleted (반복) ---

    @Test
    fun `toggleCompleted는 반복 항목을 다음 회차로 전진시키고 이전 알림을 취소한 뒤 재예약한다`() = runTest {
        val rule = RecurrenceRule(frequency = RecurrenceFrequency.DAILY)
        val dueAt = Instant.parse("2024-01-01T00:00:00Z")
        val item = makeItem("반복 할 일", isCompleted = false, firestoreId = "id-1", recurrence = rule, dueAt = dueAt)
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
            isCompleted = true,
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

    // --- helpers ---

    private fun makeItem(
        title: String,
        isCompleted: Boolean = false,
        categoryId: String? = null,
        tags: List<String> = emptyList(),
        firestoreId: String? = null,
        recurrence: RecurrenceRule? = null,
        dueAt: Instant? = null,
    ) = TodoItem(
        firestoreId = firestoreId,
        title = title,
        isCompleted = isCompleted,
        categoryId = categoryId,
        tags = tags,
        recurrence = recurrence,
        dueAt = dueAt,
    )

    private fun makeCategory(name: String, docId: String = "") = TodoCategory(
        docId = docId,
        name = name,
        emoji = "📁",
        colorHex = "#000000",
    )
}
