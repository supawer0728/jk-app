package com.jkapp.finance.investment

import android.content.Intent
import com.jkapp.auth.FakeAuthRepository
import com.jkapp.common.todayDate
import io.mockk.mockk
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DailyAssetInvestmentViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeInvestmentFirestoreRepository
    private lateinit var fakeSheetRepository: FakeInvestmentSheetRepository
    private lateinit var fakeAuthRepository: FakeAuthRepository
    private lateinit var viewModel: DailyAssetInvestmentViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeInvestmentFirestoreRepository()
        fakeSheetRepository = FakeInvestmentSheetRepository()
        fakeAuthRepository = FakeAuthRepository()
        viewModel = DailyAssetInvestmentViewModel(
            repository = fakeRepository,
            sheetRepository = fakeSheetRepository,
            authRepository = fakeAuthRepository,
        )
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
        quantity = BigDecimal("10"),
        purchaseAmount = CurrencyAmount(currency = "KRW", amount = purchaseAmount),
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
    fun `latestDate는 오늘 이하 전체 명의 통틀어 가장 최신 날짜를 반환한다`() = runTest {
        fakeRepository.setDailyAssetInvestments(
            listOf(
                DailyAssetInvestment(date = "2026-07-01", owner = "전지훈", investments = listOf(makeItem())),
                DailyAssetInvestment(date = "2026-07-02", owner = "권유경", investments = listOf(makeItem())),
            )
        )
        advanceUntilIdle()

        // 명의 구분 없이 전체 통틀어 가장 최신 날짜(2026-07-02).
        assertEquals("2026-07-02", viewModel.latestDate.value)
    }

    @Test
    fun `latestDate는 오늘보다 미래인 날짜는 제외한다`() = runTest {
        // 오늘은 2026-07-12 (테스트 환경 currentDate). 2026-08-01은 미래라 제외되어야 한다.
        fakeRepository.setDailyAssetInvestments(
            listOf(
                DailyAssetInvestment(date = "2026-07-05", owner = "전지훈", investments = listOf(makeItem())),
                DailyAssetInvestment(date = "2099-08-01", owner = "권유경", investments = listOf(makeItem())),
            )
        )
        advanceUntilIdle()

        assertEquals("2026-07-05", viewModel.latestDate.value)
    }

    @Test
    fun `latestOwnerItemPairs는 최신 날짜의 전체 명의 종목을 owner와 함께 병합한다`() = runTest {
        fakeRepository.setDailyAssetInvestments(
            listOf(
                DailyAssetInvestment(date = "2026-07-02", owner = "전지훈", investments = listOf(makeItem(investmentName = "삼성전자"))),
                DailyAssetInvestment(date = "2026-07-02", owner = "권유경", investments = listOf(makeItem(investmentName = "네이버"))),
                // 최신 날짜(2026-07-02)가 아닌 문서는 제외된다.
                DailyAssetInvestment(date = "2026-07-01", owner = "전지훈", investments = listOf(makeItem(investmentName = "카카오"))),
            )
        )
        advanceUntilIdle()

        val pairs = viewModel.latestOwnerItemPairs.value
        assertEquals(
            setOf("전지훈" to "삼성전자", "권유경" to "네이버"),
            pairs.map { it.first to it.second.investmentName }.toSet(),
        )
    }

    @Test
    fun `investmentRowMetrics는 최신 날짜 전체 명의 종목의 수익금을 계산한다`() = runTest {
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
    fun `applyFilter는 축 간 AND, 축 내 OR로 표시 종목을 거른다`() = runTest {
        fakeRepository.setDailyAssetInvestments(
            listOf(
                DailyAssetInvestment(
                    date = "2026-07-02", owner = "전지훈",
                    investments = listOf(
                        makeItem(investmentName = "삼성전자"),
                        makeItem(investmentName = "네이버"),
                    ),
                ),
                DailyAssetInvestment(
                    date = "2026-07-02", owner = "권유경",
                    investments = listOf(makeItem(investmentName = "카카오")),
                ),
            )
        )
        advanceUntilIdle()

        // 소유주=전지훈 AND 종목명∈{삼성전자} → 삼성전자만 남는다.
        viewModel.applyFilter(
            InvestmentFilter(owners = setOf("전지훈"), stockNames = setOf("삼성전자"))
        )
        advanceUntilIdle()

        assertEquals(
            listOf("삼성전자"),
            viewModel.investmentRowMetrics.value.map { it.item.investmentName },
        )
    }

    @Test
    fun `필터 선택지는 필터 적용 여부와 무관하게 최신 날짜 전체 데이터 기준이다`() = runTest {
        fakeRepository.setDailyAssetInvestments(
            listOf(
                DailyAssetInvestment(
                    date = "2026-07-02", owner = "전지훈",
                    investments = listOf(makeItem(investmentName = "삼성전자")),
                ),
                DailyAssetInvestment(
                    date = "2026-07-02", owner = "권유경",
                    investments = listOf(makeItem(investmentName = "네이버")),
                ),
            )
        )
        advanceUntilIdle()

        viewModel.applyFilter(InvestmentFilter(owners = setOf("전지훈")))
        advanceUntilIdle()

        // 필터를 걸어도 선택지 목록은 줄어들지 않는다(전체 명의 기준).
        assertEquals(listOf("권유경", "전지훈"), viewModel.ownerFilterOptions.value)
        assertEquals(listOf("네이버", "삼성전자"), viewModel.stockNameFilterOptions.value)
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
    fun `addInvestment로 더 최신 날짜에 저장하면 latestDate가 그 날짜로 갱신된다`() = runTest {
        fakeRepository.setDailyAssetInvestments(
            listOf(DailyAssetInvestment(date = "2026-07-01", owner = "전지훈", investments = listOf(makeItem())))
        )
        advanceUntilIdle()
        assertEquals("2026-07-01", viewModel.latestDate.value)

        viewModel.addInvestment("2026-07-10", "전지훈", makeItem(investmentName = "카카오"))
        advanceUntilIdle()

        assertEquals("2026-07-10", viewModel.latestDate.value)
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
    fun `데이터가 없으면 latestDate는 null이다`() = runTest {
        fakeRepository.setDailyAssetInvestments(emptyList())
        advanceUntilIdle()

        assertNull(viewModel.latestDate.value)
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
    fun `importInvestments는 새 문서에 여러 종목을 한 번에 추가한다`() = runTest {
        advanceUntilIdle()

        viewModel.importInvestments("2026-07-04", "권유경", listOf(makeItem(investmentName = "카카오"), makeItem(investmentName = "삼성전자")))
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetInvestmentUiState.Success
        val doc = state.investments.find { it.date == "2026-07-04" && it.owner == "권유경" }
        assertEquals(setOf("카카오", "삼성전자"), doc?.investments?.map { it.investmentName }?.toSet())
    }

    @Test
    fun `importInvestments는 기존 종목의 시세와 매수금액(통화 포함)을 갱신한다`() = runTest {
        val existing = makeItem(valuationAmount = BigDecimal("700000"), purchaseAmount = BigDecimal("650000"))
        fakeRepository.setDailyAssetInvestments(
            listOf(DailyAssetInvestment(date = "2026-07-04", owner = "전지훈", investments = listOf(existing)))
        )
        advanceUntilIdle()

        val imported = existing.copy(
            valuationAmount = BigDecimal("800000"),
            purchaseAmount = CurrencyAmount(currency = "USD", amount = BigDecimal("700000")),
        )
        viewModel.importInvestments("2026-07-04", "전지훈", listOf(imported))
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetInvestmentUiState.Success
        val doc = state.investments.find { it.date == "2026-07-04" && it.owner == "전지훈" }
        val updated = doc?.investments?.single()
        assertEquals(BigDecimal("800000"), updated?.valuationAmount)
        assertEquals(imported.purchaseAmount, updated?.purchaseAmount)
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
    fun `addInvestment는 계좌·카테고리·투자종목이 같은 항목이 이미 있으면 actionError를 설정하고 추가하지 않는다`() = runTest {
        val existing = makeItem(investmentName = "삼성전자")
        fakeRepository.setDailyAssetInvestments(
            listOf(DailyAssetInvestment(date = "2026-07-04", owner = "전지훈", investments = listOf(existing)))
        )
        advanceUntilIdle()

        viewModel.addInvestment("2026-07-04", "전지훈", makeItem(investmentName = "삼성전자", valuationAmount = BigDecimal("900000")))
        advanceUntilIdle()

        assertTrue(viewModel.actionError.value!!.contains("이미 같은"))
        val state = viewModel.uiState.value as DailyAssetInvestmentUiState.Success
        val doc = state.investments.find { it.date == "2026-07-04" && it.owner == "전지훈" }
        assertEquals(listOf(existing), doc?.investments)
    }

    @Test
    fun `updateInvestment는 다른 항목과 계좌·카테고리·투자종목이 겹치면 actionError를 설정하고 바꾸지 않는다`() = runTest {
        val kakao = makeItem(investmentName = "카카오")
        val samsung = makeItem(investmentName = "삼성전자")
        fakeRepository.setDailyAssetInvestments(
            listOf(DailyAssetInvestment(date = "2026-07-04", owner = "전지훈", investments = listOf(kakao, samsung)))
        )
        advanceUntilIdle()

        viewModel.updateInvestment("2026-07-04", "전지훈", kakao, kakao.copy(investmentName = "삼성전자"))
        advanceUntilIdle()

        assertTrue(viewModel.actionError.value!!.contains("이미 같은"))
        val state = viewModel.uiState.value as DailyAssetInvestmentUiState.Success
        val doc = state.investments.find { it.date == "2026-07-04" && it.owner == "전지훈" }
        assertEquals(setOf("카카오", "삼성전자"), doc?.investments?.map { it.investmentName }?.toSet())
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

    // --- 구글시트에서 가져오기 흐름 ---

    private val sheetHeader = listOf("계좌", "카테고리", "투자 종목", "1주 가격", "평가 금액(원화)", "보유수량", "매수금액")

    private fun sheetBlock(owner: String, vararg investmentNames: String) = InvestmentSheetBlock(
        owner = owner,
        rows = listOf(sheetHeader) + investmentNames.map { name ->
            listOf("주식계좌", "국내주식", name, "70000", "700000", "10", "650000")
        },
    )

    @Test
    fun `로그인된 계정이 시트 저장소에 전달된다`() = runTest {
        fakeAuthRepository.currentEmail = "user@example.com"
        fakeAuthRepository.setLoggedIn(true)
        advanceUntilIdle()

        assertEquals("user@example.com", fakeSheetRepository.selectedAccount)
    }

    @Test
    fun `importFromSheet 성공 시 명의별로 파싱한 미리보기 상태가 된다`() = runTest {
        advanceUntilIdle()
        fakeSheetRepository.blocks = listOf(
            sheetBlock("전지훈", "삼성전자", "카카오"),
            sheetBlock("권유경", "네이버"),
        )

        viewModel.importFromSheet()
        advanceUntilIdle()

        val preview = viewModel.sheetImport.value as InvestmentSheetImportState.Preview
        assertEquals(listOf("전지훈", "권유경"), preview.blocks.map { it.owner })
        assertEquals(
            listOf("삼성전자", "카카오"),
            preview.blocks.first { it.owner == "전지훈" }.rows.mapNotNull { it.item?.investmentName },
        )
        assertEquals(
            listOf("네이버"),
            preview.blocks.first { it.owner == "권유경" }.rows.mapNotNull { it.item?.investmentName },
        )
        assertNull(viewModel.actionError.value)
    }

    @Test
    fun `confirmSheetImport는 명의별로 오늘 날짜 문서에 저장하고 미리보기를 닫는다`() = runTest {
        advanceUntilIdle()
        val today = todayDate()
        fakeSheetRepository.blocks = listOf(
            sheetBlock("전지훈", "삼성전자"),
            sheetBlock("권유경", "네이버"),
        )
        viewModel.importFromSheet()
        advanceUntilIdle()

        viewModel.confirmSheetImport(today)
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetInvestmentUiState.Success
        assertEquals(
            listOf("삼성전자"),
            state.investments.single { it.date == today && it.owner == "전지훈" }.investments.map { it.investmentName },
        )
        assertEquals(
            listOf("네이버"),
            state.investments.single { it.date == today && it.owner == "권유경" }.investments.map { it.investmentName },
        )
        assertEquals(InvestmentSheetImportState.Idle, viewModel.sheetImport.value)
    }

    @Test
    fun `confirmSheetImport는 기존 종목은 시세를 수정하고 없는 종목은 새로 추가한다`() = runTest {
        val today = todayDate()
        val existing = makeItem(investmentName = "삼성전자", valuationAmount = BigDecimal("700000"))
        fakeRepository.setDailyAssetInvestments(
            listOf(DailyAssetInvestment(date = today, owner = "전지훈", investments = listOf(existing)))
        )
        advanceUntilIdle()
        fakeSheetRepository.blocks = listOf(
            // 삼성전자는 기존 종목(평가금액이 800000으로 갱신), 카카오는 신규.
            InvestmentSheetBlock(
                owner = "전지훈",
                rows = listOf(
                    sheetHeader,
                    listOf("주식계좌", "국내주식", "삼성전자", "70000", "800000", "10", "650000"),
                    listOf("주식계좌", "국내주식", "카카오", "70000", "700000", "10", "650000"),
                ),
            ),
        )
        viewModel.importFromSheet()
        advanceUntilIdle()

        viewModel.confirmSheetImport(today)
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetInvestmentUiState.Success
        val doc = state.investments.single { it.date == today && it.owner == "전지훈" }
        assertEquals(setOf("삼성전자", "카카오"), doc.investments.map { it.investmentName }.toSet())
        assertEquals(BigDecimal("800000"), doc.investments.single { it.investmentName == "삼성전자" }.valuationAmount)
    }

    @Test
    fun `confirmSheetImport는 두 명의 블록을 모두 오늘 날짜 문서로 저장한다`() = runTest {
        advanceUntilIdle()
        val today = todayDate()
        fakeSheetRepository.blocks = listOf(
            sheetBlock("전지훈", "삼성전자"),
            sheetBlock("권유경", "네이버"),
        )
        viewModel.importFromSheet()
        advanceUntilIdle()

        viewModel.confirmSheetImport(today)
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetInvestmentUiState.Success
        assertEquals(
            listOf("삼성전자"),
            state.investments.single { it.date == today && it.owner == "전지훈" }.investments.map { it.investmentName },
        )
        assertEquals(
            listOf("네이버"),
            state.investments.single { it.date == today && it.owner == "권유경" }.investments.map { it.investmentName },
        )
        // 명의 탭이 제거되어 latestDate는 오늘 날짜가 된다.
        assertEquals(today, viewModel.latestDate.value)
    }

    @Test
    fun `confirmSheetImport는 전량 파싱 실패한 명의 블록은 문서를 만들지 않는다`() = runTest {
        advanceUntilIdle()
        val today = todayDate()
        fakeSheetRepository.blocks = listOf(
            sheetBlock("전지훈", "삼성전자"),
            // 권유경 블록은 투자 종목이 비어 전량 에러 → 저장되지 않아야 한다.
            InvestmentSheetBlock(
                owner = "권유경",
                rows = listOf(sheetHeader, listOf("주식계좌", "국내주식", "", "70000", "700000", "10", "650000")),
            ),
        )
        viewModel.importFromSheet()
        advanceUntilIdle()

        viewModel.confirmSheetImport(today)
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetInvestmentUiState.Success
        assertNotNull(state.investments.find { it.date == today && it.owner == "전지훈" })
        assertNull(state.investments.find { it.date == today && it.owner == "권유경" })
        assertNull(viewModel.actionError.value)
    }

    @Test
    fun `importFromSheet 인증 예외 시 복구 인텐트가 설정되고 미리보기는 열리지 않는다`() = runTest {
        advanceUntilIdle()
        fakeSheetRepository.error = InvestmentSheetAuthException(mockk<Intent>(relaxed = true))

        viewModel.importFromSheet()
        advanceUntilIdle()

        assertNotNull(viewModel.sheetAuthRecoveryIntent.value)
        assertEquals(InvestmentSheetImportState.Idle, viewModel.sheetImport.value)
        assertNull(viewModel.actionError.value)
    }

    @Test
    fun `importFromSheet 일반 오류 시 actionError가 설정된다`() = runTest {
        advanceUntilIdle()
        fakeSheetRepository.error = RuntimeException("네트워크 오류")

        viewModel.importFromSheet()
        advanceUntilIdle()

        assertTrue(viewModel.actionError.value!!.contains("구글시트를 불러오지 못했습니다"))
        assertEquals(InvestmentSheetImportState.Idle, viewModel.sheetImport.value)
    }

    @Test
    fun `로딩 중 dismissSheetImport로 취소하면 Idle로 돌아가고 오류를 남기지 않는다`() = runTest {
        advanceUntilIdle()
        fakeSheetRepository.suspendIndefinitely = true

        viewModel.importFromSheet()
        // 시계를 진행시키지 않고 현재 시점 작업만 실행한다(타임아웃 30초는 아직 발화하지 않음).
        runCurrent()
        assertEquals(InvestmentSheetImportState.Loading, viewModel.sheetImport.value)

        viewModel.dismissSheetImport()
        advanceUntilIdle()

        assertEquals(InvestmentSheetImportState.Idle, viewModel.sheetImport.value)
        assertNull(viewModel.actionError.value)
    }

    @Test
    fun `응답이 타임아웃되면 actionError가 설정되고 Idle로 돌아간다`() = runTest {
        advanceUntilIdle()
        fakeSheetRepository.suspendIndefinitely = true

        viewModel.importFromSheet()
        advanceUntilIdle() // 가상 시계가 타임아웃(30초)을 지나 withTimeoutOrNull이 null을 반환한다.

        assertTrue(viewModel.actionError.value!!.contains("시간이 초과"))
        assertEquals(InvestmentSheetImportState.Idle, viewModel.sheetImport.value)
    }
}
