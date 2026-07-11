package com.jkapp.finance.benchmark

import android.content.Intent
import com.jkapp.auth.FakeAuthRepository
import com.jkapp.common.todayDate
import io.mockk.mockk
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BenchmarkViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeBenchmarkFirestoreRepository
    private lateinit var fakeSheetRepository: FakeBenchmarkSheetRepository
    private lateinit var fakeAuthRepository: FakeAuthRepository
    private lateinit var viewModel: BenchmarkViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeBenchmarkFirestoreRepository()
        fakeSheetRepository = FakeBenchmarkSheetRepository()
        fakeAuthRepository = FakeAuthRepository()
        viewModel = BenchmarkViewModel(
            repository = fakeRepository,
            sheetRepository = fakeSheetRepository,
            authRepository = fakeAuthRepository,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeBenchmark(date: String, currentAmount: BigDecimal = BigDecimal("1000000")) = Benchmark(
        date = date,
        additionalInvestment = BigDecimal("1000000"),
        currentAmount = currentAmount,
        kospi = BigDecimal("2500"),
        snp500 = BigDecimal("5000"),
        nasdaq = BigDecimal("16000"),
    )

    @Test
    fun `초기에는 uiState가 Loading이다가 데이터 수집 후 Success가 된다`() = runTest {
        val benchmarks = listOf(makeBenchmark("2026-07-01"))
        fakeRepository.setBenchmarks(benchmarks)
        advanceUntilIdle()

        val state = viewModel.uiState.value as BenchmarkUiState.Success
        assertEquals(benchmarks, state.benchmarks)
    }

    @Test
    fun `rowMetrics는 uiState의 벤치마크를 withRowMetrics로 변환한 값을 최신 날짜부터 흘려보낸다`() = runTest {
        val benchmarks = listOf(makeBenchmark("2026-07-01"), makeBenchmark("2026-07-02"))
        fakeRepository.setBenchmarks(benchmarks)
        advanceUntilIdle()

        assertEquals(benchmarks.withRowMetrics().reversed(), viewModel.rowMetrics.value)
    }

    @Test
    fun `rowMetrics는 데이터가 없으면 빈 목록이다`() = runTest {
        advanceUntilIdle()

        assertEquals(emptyList<Any>(), viewModel.rowMetrics.value)
    }

    @Test
    fun `saveBenchmark는 새 날짜의 벤치마크를 추가한다`() = runTest {
        advanceUntilIdle()

        viewModel.saveBenchmark(makeBenchmark("2026-07-04"))
        advanceUntilIdle()

        val state = viewModel.uiState.value as BenchmarkUiState.Success
        assertEquals(1, state.benchmarks.size)
        assertEquals("2026-07-04", state.benchmarks.single().date)
    }

    @Test
    fun `saveBenchmark는 기존 날짜의 벤치마크를 덮어쓴다`() = runTest {
        fakeRepository.setBenchmarks(listOf(makeBenchmark("2026-07-04", currentAmount = BigDecimal("1000000"))))
        advanceUntilIdle()

        viewModel.saveBenchmark(makeBenchmark("2026-07-04", currentAmount = BigDecimal("1200000")))
        advanceUntilIdle()

        val state = viewModel.uiState.value as BenchmarkUiState.Success
        assertEquals(1, state.benchmarks.size)
        assertEquals(BigDecimal("1200000"), state.benchmarks.single().currentAmount)
    }

    @Test
    fun `saveBenchmark 실패 시 uiState는 유지되고 actionError가 설정된다`() = runTest {
        fakeRepository.setBenchmarks(listOf(makeBenchmark("2026-07-01")))
        advanceUntilIdle()

        fakeRepository.upsertBenchmarkError = RuntimeException("저장 실패")
        viewModel.saveBenchmark(makeBenchmark("2026-07-04"))
        advanceUntilIdle()

        assertTrue(viewModel.actionError.value!!.contains("벤치마크 저장에 실패했습니다"))
        val state = viewModel.uiState.value as BenchmarkUiState.Success
        assertEquals(listOf(makeBenchmark("2026-07-01")), state.benchmarks)
    }

    @Test
    fun `consumeActionError는 actionError를 비운다`() = runTest {
        advanceUntilIdle()

        fakeRepository.upsertBenchmarkError = RuntimeException("저장 실패")
        viewModel.saveBenchmark(makeBenchmark("2026-07-04"))
        advanceUntilIdle()

        viewModel.consumeActionError()

        assertNull(viewModel.actionError.value)
    }

    @Test
    fun `deleteBenchmark는 해당 날짜의 벤치마크를 제거한다`() = runTest {
        fakeRepository.setBenchmarks(listOf(makeBenchmark("2026-07-04")))
        advanceUntilIdle()

        viewModel.deleteBenchmark("2026-07-04")
        advanceUntilIdle()

        val state = viewModel.uiState.value as BenchmarkUiState.Success
        assertNull(state.benchmarks.find { it.date == "2026-07-04" })
    }

    @Test
    fun `deleteBenchmarks는 여러 날짜를 한 번에 삭제한다`() = runTest {
        fakeRepository.setBenchmarks(
            listOf(makeBenchmark("2026-07-01"), makeBenchmark("2026-07-02"), makeBenchmark("2026-07-03"))
        )
        advanceUntilIdle()

        viewModel.deleteBenchmarks(listOf("2026-07-01", "2026-07-02"))
        advanceUntilIdle()

        val state = viewModel.uiState.value as BenchmarkUiState.Success
        assertEquals(setOf("2026-07-03"), state.benchmarks.map { it.date }.toSet())
    }

    @Test
    fun `deleteBenchmarks는 배치가 실패하면 아무것도 삭제되지 않고 actionError가 설정된다`() = runTest {
        fakeRepository.setBenchmarks(
            listOf(makeBenchmark("2026-07-01"), makeBenchmark("2026-07-02"), makeBenchmark("2026-07-03"))
        )
        advanceUntilIdle()

        fakeRepository.deleteBenchmarkErrorDates = setOf("2026-07-02")
        viewModel.deleteBenchmarks(listOf("2026-07-01", "2026-07-02", "2026-07-03"))
        advanceUntilIdle()

        val state = viewModel.uiState.value as BenchmarkUiState.Success
        assertEquals(setOf("2026-07-01", "2026-07-02", "2026-07-03"), state.benchmarks.map { it.date }.toSet())
        assertTrue(viewModel.actionError.value!!.contains("벤치마크 삭제에 실패했습니다"))
    }

    @Test
    fun `deleteAllBenchmarks는 컬렉션 전체를 삭제한다`() = runTest {
        fakeRepository.setBenchmarks(
            listOf(makeBenchmark("2026-07-01"), makeBenchmark("2026-07-02"))
        )
        advanceUntilIdle()

        viewModel.deleteAllBenchmarks()
        advanceUntilIdle()

        val state = viewModel.uiState.value as BenchmarkUiState.Success
        assertEquals(emptyList<Benchmark>(), state.benchmarks)
    }

    @Test
    fun `deleteAllBenchmarks 실패 시 uiState는 유지되고 actionError가 설정된다`() = runTest {
        fakeRepository.setBenchmarks(listOf(makeBenchmark("2026-07-01")))
        advanceUntilIdle()

        fakeRepository.deleteAllBenchmarksError = RuntimeException("삭제 실패")
        viewModel.deleteAllBenchmarks()
        advanceUntilIdle()

        assertTrue(viewModel.actionError.value!!.contains("벤치마크 삭제에 실패했습니다"))
        val state = viewModel.uiState.value as BenchmarkUiState.Success
        assertEquals(1, state.benchmarks.size)
    }

    @Test
    fun `deleteBenchmarks는 빈 목록이면 아무 것도 호출하지 않는다`() = runTest {
        fakeRepository.setBenchmarks(listOf(makeBenchmark("2026-07-01")))
        advanceUntilIdle()

        viewModel.deleteBenchmarks(emptyList())
        advanceUntilIdle()

        val state = viewModel.uiState.value as BenchmarkUiState.Success
        assertEquals(1, state.benchmarks.size)
        assertNull(viewModel.actionError.value)
    }

    @Test
    fun `latestCurrentAmount는 가장 최신 날짜의 현재금액을 반환한다`() = runTest {
        fakeRepository.setBenchmarks(
            listOf(
                makeBenchmark("2026-07-01", currentAmount = BigDecimal("1000000")),
                makeBenchmark("2026-07-02", currentAmount = BigDecimal("1200000")),
            )
        )
        advanceUntilIdle()

        assertEquals(BigDecimal("1200000"), viewModel.latestCurrentAmount.value)
    }

    @Test
    fun `latestCurrentAmount는 미래 날짜를 제외하고 오늘 이전 최신 값을 사용한다`() = runTest {
        val today = todayDate()
        val tomorrow = LocalDate.parse(today, DateTimeFormatter.ISO_LOCAL_DATE).plusDays(1)
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
        fakeRepository.setBenchmarks(
            listOf(
                makeBenchmark(today, currentAmount = BigDecimal("1000000")),
                makeBenchmark(tomorrow, currentAmount = BigDecimal("9999999")),
            )
        )
        advanceUntilIdle()

        assertEquals(BigDecimal("1000000"), viewModel.latestCurrentAmount.value)
    }

    @Test
    fun `latestCurrentAmount는 데이터가 없으면 null이다`() = runTest {
        advanceUntilIdle()

        assertNull(viewModel.latestCurrentAmount.value)
    }

    @Test
    fun `importBenchmarks는 붙여넣기로 여러 날짜를 한 번에 저장한다`() = runTest {
        advanceUntilIdle()

        viewModel.importBenchmarks(listOf(makeBenchmark("2026-07-01"), makeBenchmark("2026-07-02")))
        advanceUntilIdle()

        val state = viewModel.uiState.value as BenchmarkUiState.Success
        assertEquals(setOf("2026-07-01", "2026-07-02"), state.benchmarks.map { it.date }.toSet())
    }

    @Test
    fun `importBenchmarks는 배치가 실패하면 아무것도 저장되지 않고 actionError가 설정된다`() = runTest {
        advanceUntilIdle()

        fakeRepository.upsertBenchmarkErrorDates = setOf("2026-07-02")
        viewModel.importBenchmarks(
            listOf(makeBenchmark("2026-07-01"), makeBenchmark("2026-07-02"), makeBenchmark("2026-07-03"))
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value as BenchmarkUiState.Success
        assertEquals(emptySet<String>(), state.benchmarks.map { it.date }.toSet())
        assertTrue(viewModel.actionError.value!!.contains("벤치마크 저장에 실패했습니다"))
    }

    @Test
    fun `importBenchmarks는 빈 목록이면 아무 것도 호출하지 않는다`() = runTest {
        advanceUntilIdle()

        viewModel.importBenchmarks(emptyList())
        advanceUntilIdle()

        val state = viewModel.uiState.value as BenchmarkUiState.Success
        assertEquals(emptySet<String>(), state.benchmarks.map { it.date }.toSet())
        assertNull(viewModel.actionError.value)
    }

    private val sheetHeader = listOf("날짜", "추가투자", "현재금액", "KOSPI", "S&P500", "나스닥")

    @Test
    fun `importFromSheet 성공 시 미리보기 상태가 되고 시트 행을 파싱해 담는다`() = runTest {
        fakeSheetRepository.rows = listOf(
            sheetHeader,
            listOf("2026-07-04", "1000000", "1100000", "9000", "7500", "26000"),
            listOf("2026-07-05", "0", "1200000", "9100", "7600", "26100"),
        )

        viewModel.importFromSheet()
        advanceUntilIdle()

        val preview = viewModel.sheetImport.value as BenchmarkSheetImportState.Preview
        assertEquals(listOf("2026-07-04", "2026-07-05"), preview.rows.mapNotNull { it.benchmark?.date })
        assertNull(viewModel.actionError.value)
    }

    @Test
    fun `confirmSheetImport는 신규 날짜는 생성하고 기존 날짜는 수정한 뒤 미리보기를 닫는다`() = runTest {
        fakeRepository.setBenchmarks(listOf(makeBenchmark("2026-07-04", currentAmount = BigDecimal("999"))))
        advanceUntilIdle()
        fakeSheetRepository.rows = listOf(
            sheetHeader,
            listOf("2026-07-04", "0", "1100000", "9000", "7500", "26000"),
            listOf("2026-07-05", "0", "1200000", "9100", "7600", "26100"),
        )
        viewModel.importFromSheet()
        advanceUntilIdle()

        val preview = viewModel.sheetImport.value as BenchmarkSheetImportState.Preview
        viewModel.confirmSheetImport(preview.rows.mapNotNull { it.benchmark })
        advanceUntilIdle()

        val state = viewModel.uiState.value as BenchmarkUiState.Success
        assertEquals(setOf("2026-07-04", "2026-07-05"), state.benchmarks.map { it.date }.toSet())
        // 기존 2026-07-04는 시트 값(1100000)으로 수정된다.
        assertEquals(BigDecimal("1100000"), state.benchmarks.single { it.date == "2026-07-04" }.currentAmount)
        assertEquals(BenchmarkSheetImportState.Idle, viewModel.sheetImport.value)
    }

    @Test
    fun `importFromSheet 인증 예외 시 복구 인텐트가 설정되고 미리보기는 열리지 않는다`() = runTest {
        fakeSheetRepository.error = BenchmarkSheetAuthException(mockk<Intent>(relaxed = true))

        viewModel.importFromSheet()
        advanceUntilIdle()

        assertNotNull(viewModel.sheetAuthRecoveryIntent.value)
        assertEquals(BenchmarkSheetImportState.Idle, viewModel.sheetImport.value)
        assertNull(viewModel.actionError.value)
    }

    @Test
    fun `importFromSheet 일반 오류 시 actionError가 설정된다`() = runTest {
        fakeSheetRepository.error = RuntimeException("네트워크 오류")

        viewModel.importFromSheet()
        advanceUntilIdle()

        assertTrue(viewModel.actionError.value!!.contains("구글시트를 불러오지 못했습니다"))
        assertEquals(BenchmarkSheetImportState.Idle, viewModel.sheetImport.value)
    }

    @Test
    fun `로딩 중 dismissSheetImport로 취소하면 Idle로 돌아가고 오류를 남기지 않는다`() = runTest {
        fakeSheetRepository.suspendIndefinitely = true

        viewModel.importFromSheet()
        // 시계를 진행시키지 않고 현재 시점 작업만 실행한다(타임아웃 30초는 아직 발화하지 않음).
        runCurrent()
        assertEquals(BenchmarkSheetImportState.Loading, viewModel.sheetImport.value)

        viewModel.dismissSheetImport()
        advanceUntilIdle()

        assertEquals(BenchmarkSheetImportState.Idle, viewModel.sheetImport.value)
        assertNull(viewModel.actionError.value)
    }

    @Test
    fun `응답이 타임아웃되면 actionError가 설정되고 Idle로 돌아간다`() = runTest {
        fakeSheetRepository.suspendIndefinitely = true

        viewModel.importFromSheet()
        advanceUntilIdle() // 가상 시계가 타임아웃(30초)을 지나 withTimeoutOrNull이 null을 반환한다.

        assertTrue(viewModel.actionError.value!!.contains("시간이 초과"))
        assertEquals(BenchmarkSheetImportState.Idle, viewModel.sheetImport.value)
    }

    @Test
    fun `deleteBenchmark 실패 시 uiState는 유지되고 actionError가 설정된다`() = runTest {
        fakeRepository.setBenchmarks(listOf(makeBenchmark("2026-07-04")))
        advanceUntilIdle()

        fakeRepository.deleteBenchmarkError = RuntimeException("삭제 실패")
        viewModel.deleteBenchmark("2026-07-04")
        advanceUntilIdle()

        assertTrue(viewModel.actionError.value!!.contains("벤치마크 삭제에 실패했습니다"))
        val state = viewModel.uiState.value as BenchmarkUiState.Success
        assertEquals(1, state.benchmarks.size)
    }
}
