package com.jkapp.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TabOrderViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeTabOrderRepository
    private lateinit var viewModel: TabOrderViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeTabOrderRepository()
        viewModel = TabOrderViewModel(repository = fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `저장된 순서가 없으면 MainTab entries 기본 순서를 그대로 사용한다`() = runTest {
        viewModel.loadTabOrder("uid-1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(MainTab.entries, viewModel.tabOrder.value)
    }

    @Test
    fun `저장된 순서에 없는 신규 탭은 맨 뒤에 자동으로 붙고 홈은 선두로 고정된다`() {
        val saved = listOf(MainTab.DIARY.name, MainTab.HOME.name)

        val merged = TabOrderViewModel.mergeTabOrder(saved, MainTab.entries)

        // 저장 순서에서 홈이 두 번째였어도 최종 결과는 홈이 선두로 고정된다.
        assertEquals(
            listOf(
                MainTab.HOME, MainTab.DIARY, MainTab.ASSET, MainTab.TODO, MainTab.CALENDAR,
            ),
            merged
        )
    }

    @Test
    fun `저장된 순서에 더 이상 존재하지 않는 탭 이름이 있으면 걸러지고 홈은 선두로 고정된다`() {
        val saved = listOf("REMOVED_TAB", MainTab.ASSET.name, MainTab.HOME.name)

        val merged = TabOrderViewModel.mergeTabOrder(saved, MainTab.entries)

        assertEquals(
            listOf(
                MainTab.HOME, MainTab.ASSET, MainTab.DIARY, MainTab.TODO, MainTab.CALENDAR,
            ),
            merged
        )
    }

    @Test
    fun `beginEdit 호출 시 editTabOrder가 홈을 제외한 나머지 콘텐츠 탭으로 초기화된다`() = runTest {
        viewModel.loadTabOrder("uid-1")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.beginEdit()

        // 홈은 항상 선두 고정이라 재배치 대상 목록에서 빠진다.
        assertEquals(
            listOf(MainTab.ASSET, MainTab.DIARY, MainTab.TODO, MainTab.CALENDAR),
            viewModel.editTabOrder.value
        )
    }

    @Test
    fun `moveTab으로 editTabOrder(홈 제외) 내 탭 위치를 옮기면 순서가 즉시 반영된다`() = runTest {
        viewModel.loadTabOrder("uid-1")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.beginEdit()

        // [ASSET, DIARY, TODO, CALENDAR]에서 index 0(ASSET)을 index 2로 이동
        viewModel.moveTab(0, 2)

        assertEquals(
            listOf(MainTab.DIARY, MainTab.TODO, MainTab.ASSET, MainTab.CALENDAR),
            viewModel.editTabOrder.value
        )
    }

    @Test
    fun `moveTab은 editTabOrder가 null이면 아무것도 하지 않는다`() = runTest {
        viewModel.loadTabOrder("uid-1")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.moveTab(0, 2)

        // editTabOrder가 null이므로 tabOrder는 변경되지 않아야 한다
        assertEquals(MainTab.entries, viewModel.tabOrder.value)
        assertNull(viewModel.editTabOrder.value)
    }

    @Test
    fun `applyEdit 호출 시 홈 선두 + 재배치된 나머지 순서로 tabOrder에 반영되고 저장된다`() = runTest {
        viewModel.loadTabOrder("uid-1")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.beginEdit()
        // [ASSET, DIARY, TODO, CALENDAR] → index 0(ASSET)을 index 1로 → [DIARY, ASSET, TODO, CALENDAR]
        viewModel.moveTab(0, 1)

        viewModel.applyEdit()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.editTabOrder.value)
        // 홈이 항상 선두를 유지한 채 나머지만 재배치되어야 한다.
        assertEquals(
            listOf(MainTab.HOME, MainTab.DIARY, MainTab.ASSET, MainTab.TODO, MainTab.CALENDAR),
            viewModel.tabOrder.value
        )
        assertEquals("uid-1", fakeRepository.lastSavedUid)
        assertEquals(viewModel.tabOrder.value.map { it.name }, fakeRepository.lastSavedOrder)
        // 저장 순서의 선두는 반드시 홈이어야 한다.
        assertEquals(MainTab.HOME.name, fakeRepository.lastSavedOrder?.first())
    }

    @Test
    fun `applyEdit은 변경이 없으면 저장을 호출하지 않는다`() = runTest {
        viewModel.loadTabOrder("uid-1")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.beginEdit()

        viewModel.applyEdit()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(fakeRepository.lastSavedUid)
        assertNull(viewModel.editTabOrder.value)
    }

    @Test
    fun `cancelEdit 호출 시 변경을 버리고 편집이 종료된다`() = runTest {
        viewModel.loadTabOrder("uid-1")
        testDispatcher.scheduler.advanceUntilIdle()
        val originalOrder = viewModel.tabOrder.value
        viewModel.beginEdit()
        viewModel.moveTab(0, 1)

        viewModel.cancelEdit()

        assertNull(viewModel.editTabOrder.value)
        // tabOrder는 변경되지 않아야 한다
        assertEquals(originalOrder, viewModel.tabOrder.value)
        assertNull(fakeRepository.lastSavedUid)
    }
}
