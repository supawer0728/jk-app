package com.jkapp.ui

import com.jkapp.data.firestore.FakeTabOrderRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
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
    fun `저장된 순서에 없는 신규 탭은 맨 뒤에 자동으로 붙는다`() {
        val saved = listOf(MainTab.DIARY.name, MainTab.HOME.name)

        val merged = TabOrderViewModel.mergeTabOrder(saved, MainTab.entries)

        assertEquals(
            listOf(
                MainTab.DIARY, MainTab.HOME, MainTab.ASSET, MainTab.TODO, MainTab.CALENDAR,
                MainTab.DM1, MainTab.DM2, MainTab.DM3,
            ),
            merged
        )
    }

    @Test
    fun `저장된 순서에 더 이상 존재하지 않는 탭 이름이 있으면 걸러진다`() {
        val saved = listOf("REMOVED_TAB", MainTab.ASSET.name, MainTab.HOME.name)

        val merged = TabOrderViewModel.mergeTabOrder(saved, MainTab.entries)

        assertEquals(
            listOf(
                MainTab.ASSET, MainTab.HOME, MainTab.DIARY, MainTab.TODO, MainTab.CALENDAR,
                MainTab.DM1, MainTab.DM2, MainTab.DM3,
            ),
            merged
        )
    }

    @Test
    fun `moveTab으로 탭 위치를 옮기면 순서가 즉시 반영된다`() = runTest {
        viewModel.loadTabOrder("uid-1")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.moveTab(0, 2)

        assertEquals(
            listOf(
                MainTab.ASSET, MainTab.DIARY, MainTab.HOME, MainTab.TODO, MainTab.CALENDAR,
                MainTab.DM1, MainTab.DM2, MainTab.DM3,
            ),
            viewModel.tabOrder.value
        )
    }

    @Test
    fun `완료를 누르면 편집 모드가 종료되고 현재 순서가 저장된다`() = runTest {
        viewModel.loadTabOrder("uid-1")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.moveTab(0, 1)

        viewModel.toggleEditMode() // 수정 진입
        assertEquals(true, viewModel.isEditMode.value)

        viewModel.toggleEditMode() // 완료 -> 저장 + 편집 모드 종료
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, viewModel.isEditMode.value)
        assertEquals("uid-1", fakeRepository.lastSavedUid)
        assertEquals(viewModel.tabOrder.value.map { it.name }, fakeRepository.lastSavedOrder)
    }

    @Test
    fun `수정 진입만 했을 때는 저장을 호출하지 않는다`() = runTest {
        viewModel.loadTabOrder("uid-1")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleEditMode()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(null, fakeRepository.lastSavedUid)
    }
}
