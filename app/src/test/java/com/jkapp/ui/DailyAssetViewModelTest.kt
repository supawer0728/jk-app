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
    fun `parsePasteText는 파서 결과를 그대로 반환한다`() = runTest {
        val text = "현금\tJ\t-\t-\t-\t₩ 1,000"

        val result = viewModel.parsePasteText(text, hasHeader = false)

        assertEquals(1, result.size)
        assertEquals("전지훈", result.single().item?.owner)
    }

    @Test
    fun `parsePasteText는 파싱 실패 행이 있어도 예외 없이 전체 결과를 반환한다`() = runTest {
        val text = "컬럼부족\tJ"

        val result = viewModel.parsePasteText(text, hasHeader = false)

        assertEquals(1, result.size)
        assertNull(result.single().item)
        assertTrue(result.single().error!!.isNotBlank())
    }

    @Test
    fun `importAssets는 이름과 명의가 같은 항목을 교체하고 없는 항목은 추가한다`() = runTest {
        fakeRepository.setDailyAssets(
            listOf(DailyAsset(date = "2026-07-04", assets = listOf(makeAsset("현금", amount = BigDecimal("100")))))
        )
        advanceUntilIdle()

        viewModel.importAssets(
            "2026-07-04",
            listOf(
                makeAsset("현금", amount = BigDecimal("200")),
                makeAsset("주식"),
            ),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetUiState.Success
        val dailyAsset = state.dailyAssets.find { it.date == "2026-07-04" }
        assertEquals(
            listOf(makeAsset("현금", amount = BigDecimal("200")), makeAsset("주식")),
            dailyAsset?.assets,
        )
    }

    @Test
    fun `importAssets는 이름이 같아도 명의가 다르면 별도 항목으로 유지한다`() = runTest {
        fakeRepository.setDailyAssets(
            listOf(
                DailyAsset(
                    date = "2026-07-04",
                    assets = listOf(makeAsset("적금", owner = "전지훈", amount = BigDecimal("100"))),
                )
            )
        )
        advanceUntilIdle()

        viewModel.importAssets("2026-07-04", listOf(makeAsset("적금", owner = "권유경", amount = BigDecimal("200"))))
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetUiState.Success
        val dailyAsset = state.dailyAssets.find { it.date == "2026-07-04" }
        assertEquals(
            listOf(
                makeAsset("적금", owner = "전지훈", amount = BigDecimal("100")),
                makeAsset("적금", owner = "권유경", amount = BigDecimal("200")),
            ),
            dailyAsset?.assets,
        )
    }

    @Test
    fun `importAssets는 기존 항목의 card처럼 붙여넣기에 없는 필드는 보존한다`() = runTest {
        val existing = AssetItem(name = "현금", owner = "전지훈", card = "체크카드", amount = BigDecimal("100"))
        fakeRepository.setDailyAssets(listOf(DailyAsset(date = "2026-07-04", assets = listOf(existing))))
        advanceUntilIdle()

        viewModel.importAssets("2026-07-04", listOf(makeAsset("현금", amount = BigDecimal("200"))))
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetUiState.Success
        val dailyAsset = state.dailyAssets.find { it.date == "2026-07-04" }
        val updated = dailyAsset?.assets?.single { it.name == "현금" }
        assertEquals("체크카드", updated?.card)
        assertEquals(BigDecimal("200"), updated?.amount)
    }

    @Test
    fun `importAssets는 자산이 없는 날짜에도 새 문서를 생성한다`() = runTest {
        advanceUntilIdle()

        viewModel.importAssets("2026-07-04", listOf(makeAsset("현금"), makeAsset("주식")))
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
