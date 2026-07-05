package com.jkapp.ui

import com.jkapp.data.firestore.FakeFirestoreRepository
import com.jkapp.data.model.DailyAssetInvestment
import com.jkapp.data.model.InvestmentItem
import com.jkapp.data.model.PurchasePrice
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DailyAssetInvestmentViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeFirestoreRepository
    private lateinit var viewModel: DailyAssetInvestmentViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeFirestoreRepository()
        viewModel = DailyAssetInvestmentViewModel(repository = fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeItem(
        investmentName: String = "삼성전자",
        valuationAmount: BigDecimal = BigDecimal("700000"),
        purchaseAmount: BigDecimal = BigDecimal("650000"),
    ) = InvestmentItem(
        assetName = "주식계좌",
        category = "국내주식",
        investmentName = investmentName,
        pricePerShare = BigDecimal("70000"),
        valuationAmount = valuationAmount,
        purchasePrice = PurchasePrice(currency = "KRW", amount = BigDecimal("65000")),
        quantity = BigDecimal("10"),
        purchaseAmount = purchaseAmount,
    )

    @Test
    fun `초기에는 uiState가 Loading이다가 데이터 수집 후 Success가 된다`() = runTest {
        val investments = listOf(DailyAssetInvestment(date = "2026-07-01", owner = "전지훈", investments = listOf(makeItem())))
        fakeRepository.setDailyAssetInvestments(investments)
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetInvestmentUiState.Success
        assertEquals(investments, state.investments)
    }

    @Test
    fun `selectedOwner 기본값은 전지훈이고 availableDates는 해당 명의의 날짜만 반환한다`() = runTest {
        fakeRepository.setDailyAssetInvestments(
            listOf(
                DailyAssetInvestment(date = "2026-07-01", owner = "전지훈", investments = listOf(makeItem())),
                DailyAssetInvestment(date = "2026-07-02", owner = "권유경", investments = listOf(makeItem())),
            )
        )
        advanceUntilIdle()

        assertEquals("전지훈", viewModel.selectedOwner.value)
        assertEquals(listOf("2026-07-01"), viewModel.availableDates.value)
        assertEquals("2026-07-01", viewModel.selectedDate.value)
    }

    @Test
    fun `selectOwner로 명의를 전환하면 availableDates와 selectedDate가 해당 명의 기준으로 갱신된다`() = runTest {
        fakeRepository.setDailyAssetInvestments(
            listOf(
                DailyAssetInvestment(date = "2026-07-01", owner = "전지훈", investments = listOf(makeItem())),
                DailyAssetInvestment(date = "2026-07-02", owner = "권유경", investments = listOf(makeItem())),
            )
        )
        advanceUntilIdle()

        viewModel.selectOwner("권유경")
        advanceUntilIdle()

        assertEquals(listOf("2026-07-02"), viewModel.availableDates.value)
        assertEquals("2026-07-02", viewModel.selectedDate.value)
    }

    @Test
    fun `currentInvestment는 선택된 날짜와 명의에 해당하는 문서를 반환한다`() = runTest {
        val target = DailyAssetInvestment(date = "2026-07-01", owner = "전지훈", investments = listOf(makeItem()))
        fakeRepository.setDailyAssetInvestments(listOf(target))
        advanceUntilIdle()

        assertEquals(target, viewModel.currentInvestment.value)
    }

    @Test
    fun `investmentRowMetrics는 currentInvestment의 종목별 수익금을 계산한다`() = runTest {
        fakeRepository.setDailyAssetInvestments(
            listOf(
                DailyAssetInvestment(
                    date = "2026-07-01",
                    owner = "전지훈",
                    investments = listOf(makeItem(valuationAmount = BigDecimal("700000"), purchaseAmount = BigDecimal("650000"))),
                )
            )
        )
        advanceUntilIdle()

        assertEquals(BigDecimal("50000"), viewModel.investmentRowMetrics.value.single().profit)
    }

    @Test
    fun `addInvestment는 새 날짜+명의에 대해 문서를 새로 생성한다`() = runTest {
        advanceUntilIdle()

        viewModel.addInvestment("2026-07-04", "전지훈", makeItem())
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetInvestmentUiState.Success
        val doc = state.investments.find { it.date == "2026-07-04" && it.owner == "전지훈" }
        assertEquals(listOf(makeItem()), doc?.investments)
    }

    @Test
    fun `addInvestment는 기존 날짜+명의 문서에 종목을 추가한다`() = runTest {
        fakeRepository.setDailyAssetInvestments(
            listOf(DailyAssetInvestment(date = "2026-07-04", owner = "전지훈", investments = listOf(makeItem(investmentName = "카카오"))))
        )
        advanceUntilIdle()

        viewModel.addInvestment("2026-07-04", "전지훈", makeItem(investmentName = "삼성전자"))
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetInvestmentUiState.Success
        val doc = state.investments.find { it.date == "2026-07-04" && it.owner == "전지훈" }
        assertEquals(setOf("카카오", "삼성전자"), doc?.investments?.map { it.investmentName }?.toSet())
    }

    @Test
    fun `updateInvestment는 일치하는 항목을 새 값으로 교체한다`() = runTest {
        val original = makeItem(valuationAmount = BigDecimal("700000"))
        fakeRepository.setDailyAssetInvestments(
            listOf(DailyAssetInvestment(date = "2026-07-04", owner = "전지훈", investments = listOf(original)))
        )
        advanceUntilIdle()

        val updated = original.copy(valuationAmount = BigDecimal("800000"))
        viewModel.updateInvestment("2026-07-04", "전지훈", original, updated)
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetInvestmentUiState.Success
        val doc = state.investments.find { it.date == "2026-07-04" && it.owner == "전지훈" }
        assertEquals(listOf(updated), doc?.investments)
    }

    @Test
    fun `deleteInvestment로 마지막 종목을 지우면 문서 자체가 삭제된다`() = runTest {
        val item = makeItem()
        fakeRepository.setDailyAssetInvestments(
            listOf(DailyAssetInvestment(date = "2026-07-04", owner = "전지훈", investments = listOf(item)))
        )
        advanceUntilIdle()

        viewModel.deleteInvestment("2026-07-04", "전지훈", item)
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetInvestmentUiState.Success
        assertNull(state.investments.find { it.date == "2026-07-04" && it.owner == "전지훈" })
    }

    @Test
    fun `저장 실패 시 uiState는 유지되고 actionError가 설정된다`() = runTest {
        fakeRepository.setDailyAssetInvestments(
            listOf(DailyAssetInvestment(date = "2026-07-01", owner = "전지훈", investments = listOf(makeItem())))
        )
        advanceUntilIdle()

        fakeRepository.upsertDailyAssetInvestmentError = RuntimeException("저장 실패")
        viewModel.addInvestment("2026-07-04", "전지훈", makeItem(investmentName = "카카오"))
        advanceUntilIdle()

        assertTrue(viewModel.actionError.value!!.contains("투자 종목 저장에 실패했습니다"))
        val state = viewModel.uiState.value as DailyAssetInvestmentUiState.Success
        assertEquals(1, state.investments.size)
    }

    @Test
    fun `consumeActionError는 actionError를 비운다`() = runTest {
        advanceUntilIdle()

        fakeRepository.upsertDailyAssetInvestmentError = RuntimeException("저장 실패")
        viewModel.addInvestment("2026-07-04", "전지훈", makeItem())
        advanceUntilIdle()

        viewModel.consumeActionError()

        assertNull(viewModel.actionError.value)
    }

    @Test
    fun `데이터가 없는 명의로 전환하면 selectedDate는 null이 된다`() = runTest {
        fakeRepository.setDailyAssetInvestments(
            listOf(DailyAssetInvestment(date = "2026-07-01", owner = "전지훈", investments = listOf(makeItem())))
        )
        advanceUntilIdle()

        viewModel.selectOwner("권유경")
        advanceUntilIdle()

        assertEquals(emptyList<String>(), viewModel.availableDates.value)
        assertNull(viewModel.selectedDate.value)
    }

    @Test
    fun `아직 데이터가 없는 새 날짜를 선택하면 최신 날짜로 되돌려지지 않는다`() = runTest {
        fakeRepository.setDailyAssetInvestments(
            listOf(DailyAssetInvestment(date = "2026-07-01", owner = "전지훈", investments = listOf(makeItem())))
        )
        advanceUntilIdle()

        viewModel.selectDate("2026-07-10")
        advanceUntilIdle()

        assertEquals("2026-07-10", viewModel.selectedDate.value)
        assertNull(viewModel.currentInvestment.value)
    }

    @Test
    fun `deleteInvestment는 여러 종목 중 하나만 지우면 나머지 종목은 문서에 남는다`() = runTest {
        val kakao = makeItem(investmentName = "카카오")
        val samsung = makeItem(investmentName = "삼성전자")
        fakeRepository.setDailyAssetInvestments(
            listOf(DailyAssetInvestment(date = "2026-07-04", owner = "전지훈", investments = listOf(kakao, samsung)))
        )
        advanceUntilIdle()

        viewModel.deleteInvestment("2026-07-04", "전지훈", kakao)
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetInvestmentUiState.Success
        val doc = state.investments.find { it.date == "2026-07-04" && it.owner == "전지훈" }
        assertEquals(listOf(samsung), doc?.investments)
    }

    @Test
    fun `updateInvestment는 대상을 찾지 못하면 actionError를 설정하고 문서를 바꾸지 않는다`() = runTest {
        val original = makeItem()
        fakeRepository.setDailyAssetInvestments(
            listOf(DailyAssetInvestment(date = "2026-07-04", owner = "전지훈", investments = listOf(original)))
        )
        advanceUntilIdle()

        viewModel.updateInvestment("2026-07-04", "전지훈", makeItem(investmentName = "존재하지않음"), makeItem(investmentName = "새이름"))
        advanceUntilIdle()

        assertTrue(viewModel.actionError.value!!.contains("찾을 수 없습니다"))
        val state = viewModel.uiState.value as DailyAssetInvestmentUiState.Success
        val doc = state.investments.find { it.date == "2026-07-04" && it.owner == "전지훈" }
        assertEquals(listOf(original), doc?.investments)
    }

    @Test
    fun `deleteInvestment는 대상을 찾지 못하면 actionError를 설정하고 문서를 바꾸지 않는다`() = runTest {
        val original = makeItem()
        fakeRepository.setDailyAssetInvestments(
            listOf(DailyAssetInvestment(date = "2026-07-04", owner = "전지훈", investments = listOf(original)))
        )
        advanceUntilIdle()

        viewModel.deleteInvestment("2026-07-04", "전지훈", makeItem(investmentName = "존재하지않음"))
        advanceUntilIdle()

        assertTrue(viewModel.actionError.value!!.contains("찾을 수 없습니다"))
        val state = viewModel.uiState.value as DailyAssetInvestmentUiState.Success
        val doc = state.investments.find { it.date == "2026-07-04" && it.owner == "전지훈" }
        assertEquals(listOf(original), doc?.investments)
    }

    @Test
    fun `동시에 발생한 투자 종목 변경 요청은 직렬화되어 서로의 변경을 덮어쓰지 않는다`() = runTest {
        advanceUntilIdle()

        var activeCount = 0
        var maxActiveCount = 0
        fakeRepository.onUpsertDailyAssetInvestment = {
            activeCount++
            maxActiveCount = maxOf(maxActiveCount, activeCount)
            yield()
            activeCount--
        }

        viewModel.addInvestment("2026-07-04", "전지훈", makeItem(investmentName = "카카오"))
        viewModel.addInvestment("2026-07-04", "전지훈", makeItem(investmentName = "삼성전자"))
        advanceUntilIdle()

        assertEquals(1, maxActiveCount)
        val state = viewModel.uiState.value as DailyAssetInvestmentUiState.Success
        val doc = state.investments.find { it.date == "2026-07-04" && it.owner == "전지훈" }
        assertEquals(setOf("카카오", "삼성전자"), doc?.investments?.map { it.investmentName }?.toSet())
    }

    @Test
    fun `importInvestments는 새 문서에 여러 종목을 한 번에 추가하고 선택된 명의를 붙여넣은 명의로 전환한다`() = runTest {
        advanceUntilIdle()

        viewModel.importInvestments("2026-07-04", "권유경", listOf(makeItem(investmentName = "카카오"), makeItem(investmentName = "삼성전자")))
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetInvestmentUiState.Success
        val doc = state.investments.find { it.date == "2026-07-04" && it.owner == "권유경" }
        assertEquals(setOf("카카오", "삼성전자"), doc?.investments?.map { it.investmentName }?.toSet())
        assertEquals("권유경", viewModel.selectedOwner.value)
    }

    @Test
    fun `importInvestments는 기존 종목의 시세만 갱신하고 매수단가는 기존 값을 유지한다`() = runTest {
        val existing = makeItem(valuationAmount = BigDecimal("700000"), purchaseAmount = BigDecimal("650000"))
        fakeRepository.setDailyAssetInvestments(
            listOf(DailyAssetInvestment(date = "2026-07-04", owner = "전지훈", investments = listOf(existing)))
        )
        advanceUntilIdle()

        val imported = existing.copy(
            valuationAmount = BigDecimal("800000"),
            purchaseAmount = BigDecimal("650000"),
            purchasePrice = PurchasePrice(currency = "USD", amount = BigDecimal.ZERO),
        )
        viewModel.importInvestments("2026-07-04", "전지훈", listOf(imported))
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetInvestmentUiState.Success
        val doc = state.investments.find { it.date == "2026-07-04" && it.owner == "전지훈" }
        val updated = doc?.investments?.single()
        assertEquals(BigDecimal("800000"), updated?.valuationAmount)
        assertEquals(existing.purchasePrice, updated?.purchasePrice)
    }

    @Test
    fun `importInvestments는 빈 목록이면 아무 것도 저장하지 않는다`() = runTest {
        advanceUntilIdle()

        viewModel.importInvestments("2026-07-04", "전지훈", emptyList())
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetInvestmentUiState.Success
        assertEquals(emptyList<DailyAssetInvestment>(), state.investments)
    }

    @Test
    fun `deleteInvestments는 선택한 여러 종목을 한 번에 삭제한다`() = runTest {
        val kakao = makeItem(investmentName = "카카오")
        val samsung = makeItem(investmentName = "삼성전자")
        val naver = makeItem(investmentName = "네이버")
        fakeRepository.setDailyAssetInvestments(
            listOf(DailyAssetInvestment(date = "2026-07-04", owner = "전지훈", investments = listOf(kakao, samsung, naver)))
        )
        advanceUntilIdle()

        viewModel.deleteInvestments("2026-07-04", "전지훈", listOf(kakao, samsung))
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetInvestmentUiState.Success
        val doc = state.investments.find { it.date == "2026-07-04" && it.owner == "전지훈" }
        assertEquals(listOf(naver), doc?.investments)
    }

    @Test
    fun `deleteInvestments로 전체 종목을 지우면 문서 자체가 삭제된다`() = runTest {
        val kakao = makeItem(investmentName = "카카오")
        val samsung = makeItem(investmentName = "삼성전자")
        fakeRepository.setDailyAssetInvestments(
            listOf(DailyAssetInvestment(date = "2026-07-04", owner = "전지훈", investments = listOf(kakao, samsung)))
        )
        advanceUntilIdle()

        viewModel.deleteInvestments("2026-07-04", "전지훈", listOf(kakao, samsung))
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetInvestmentUiState.Success
        assertNull(state.investments.find { it.date == "2026-07-04" && it.owner == "전지훈" })
    }

    @Test
    fun `deleteInvestments는 빈 목록이면 아무 것도 삭제하지 않는다`() = runTest {
        val item = makeItem()
        fakeRepository.setDailyAssetInvestments(
            listOf(DailyAssetInvestment(date = "2026-07-04", owner = "전지훈", investments = listOf(item)))
        )
        advanceUntilIdle()

        viewModel.deleteInvestments("2026-07-04", "전지훈", emptyList())
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetInvestmentUiState.Success
        val doc = state.investments.find { it.date == "2026-07-04" && it.owner == "전지훈" }
        assertEquals(listOf(item), doc?.investments)
    }
}
