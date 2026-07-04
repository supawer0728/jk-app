package com.jkapp.ui

import com.jkapp.data.firestore.FakeFirestoreRepository
import com.jkapp.data.model.AssetItem
import com.jkapp.data.model.DailyAsset
import java.math.BigDecimal
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
class DailyAssetViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeFirestoreRepository
    private lateinit var viewModel: DailyAssetViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeFirestoreRepository()
        viewModel = DailyAssetViewModel(repository = fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeAsset(
        name: String,
        owner: String = "전지훈",
        amount: BigDecimal? = null,
    ) = AssetItem(name = name, owner = owner, amount = amount)

    @Test
    fun `초기에는 uiState가 Loading이다가 데이터 수집 후 Success가 된다`() = runTest {
        val dailyAssets = listOf(DailyAsset(date = "2026-07-01", assets = listOf(makeAsset("현금"))))
        fakeRepository.setDailyAssets(dailyAssets)
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetUiState.Success
        assertEquals(dailyAssets, state.dailyAssets)
    }

    @Test
    fun `addAsset은 새 날짜에 대해 자산 목록을 새로 생성한다`() = runTest {
        advanceUntilIdle()

        viewModel.addAsset("2026-07-04", makeAsset("현금", amount = BigDecimal("1000")))
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetUiState.Success
        val dailyAsset = state.dailyAssets.find { it.date == "2026-07-04" }
        assertEquals(listOf(makeAsset("현금", amount = BigDecimal("1000"))), dailyAsset?.assets)
    }

    @Test
    fun `addAsset은 기존 날짜의 자산 목록에 항목을 추가한다`() = runTest {
        fakeRepository.setDailyAssets(listOf(DailyAsset(date = "2026-07-04", assets = listOf(makeAsset("현금")))))
        advanceUntilIdle()

        viewModel.addAsset("2026-07-04", makeAsset("주식"))
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetUiState.Success
        val dailyAsset = state.dailyAssets.find { it.date == "2026-07-04" }
        assertEquals(listOf(makeAsset("현금"), makeAsset("주식")), dailyAsset?.assets)
    }

    @Test
    fun `updateAsset은 지정한 인덱스의 항목만 교체한다`() = runTest {
        fakeRepository.setDailyAssets(
            listOf(DailyAsset(date = "2026-07-04", assets = listOf(makeAsset("현금"), makeAsset("주식"))))
        )
        advanceUntilIdle()

        viewModel.updateAsset("2026-07-04", 1, makeAsset("주식", amount = BigDecimal("500")))
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetUiState.Success
        val dailyAsset = state.dailyAssets.find { it.date == "2026-07-04" }
        assertEquals(
            listOf(makeAsset("현금"), makeAsset("주식", amount = BigDecimal("500"))),
            dailyAsset?.assets,
        )
    }

    @Test
    fun `deleteAsset은 항목이 남아있으면 나머지 목록으로 upsert한다`() = runTest {
        fakeRepository.setDailyAssets(
            listOf(DailyAsset(date = "2026-07-04", assets = listOf(makeAsset("현금"), makeAsset("주식"))))
        )
        advanceUntilIdle()

        viewModel.deleteAsset("2026-07-04", 0)
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetUiState.Success
        val dailyAsset = state.dailyAssets.find { it.date == "2026-07-04" }
        assertEquals(listOf(makeAsset("주식")), dailyAsset?.assets)
    }

    @Test
    fun `deleteAsset은 마지막 항목을 삭제하면 문서 자체를 삭제한다`() = runTest {
        fakeRepository.setDailyAssets(listOf(DailyAsset(date = "2026-07-04", assets = listOf(makeAsset("현금")))))
        advanceUntilIdle()

        viewModel.deleteAsset("2026-07-04", 0)
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetUiState.Success
        assertNull(state.dailyAssets.find { it.date == "2026-07-04" })
    }

    @Test
    fun `자산 저장 실패 시 uiState가 Error가 된다`() = runTest {
        advanceUntilIdle()

        fakeRepository.upsertDailyAssetError = RuntimeException("저장 실패")
        viewModel.addAsset("2026-07-04", makeAsset("현금"))
        advanceUntilIdle()

        val error = viewModel.uiState.value as DailyAssetUiState.Error
        assertTrue(error.message.contains("자산 저장에 실패했습니다"))
    }

    @Test
    fun `자산 삭제 실패 시 uiState가 Error가 된다`() = runTest {
        fakeRepository.setDailyAssets(listOf(DailyAsset(date = "2026-07-04", assets = listOf(makeAsset("현금")))))
        advanceUntilIdle()

        fakeRepository.deleteDailyAssetError = RuntimeException("삭제 실패")
        viewModel.deleteAsset("2026-07-04", 0)
        advanceUntilIdle()

        val error = viewModel.uiState.value as DailyAssetUiState.Error
        assertTrue(error.message.contains("자산 삭제에 실패했습니다"))
    }
}
