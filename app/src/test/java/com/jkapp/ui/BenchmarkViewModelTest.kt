package com.jkapp.ui

import com.jkapp.data.firestore.FakeFirestoreRepository
import com.jkapp.data.model.Benchmark
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
class BenchmarkViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeFirestoreRepository
    private lateinit var viewModel: BenchmarkViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeFirestoreRepository()
        viewModel = BenchmarkViewModel(repository = fakeRepository)
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
    fun `importBenchmarks는 붙여넣기로 여러 날짜를 한 번에 저장한다`() = runTest {
        advanceUntilIdle()

        viewModel.importBenchmarks(listOf(makeBenchmark("2026-07-01"), makeBenchmark("2026-07-02")))
        advanceUntilIdle()

        val state = viewModel.uiState.value as BenchmarkUiState.Success
        assertEquals(setOf("2026-07-01", "2026-07-02"), state.benchmarks.map { it.date }.toSet())
    }

    @Test
    fun `importBenchmarks는 성공한 항목은 반영하고 실패한 날짜만 actionError로 보고한다`() = runTest {
        advanceUntilIdle()

        fakeRepository.upsertBenchmarkErrorDates = setOf("2026-07-02")
        viewModel.importBenchmarks(
            listOf(makeBenchmark("2026-07-01"), makeBenchmark("2026-07-02"), makeBenchmark("2026-07-03"))
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value as BenchmarkUiState.Success
        assertEquals(setOf("2026-07-01", "2026-07-03"), state.benchmarks.map { it.date }.toSet())
        assertTrue(viewModel.actionError.value!!.contains("2026-07-02"))
    }

    @Test
    fun `parsePasteText는 파서 결과를 그대로 반환한다`() = runTest {
        val text = listOf(
            listOf("날짜", "추가투자", "현재금액", "KOSPI", "S&P500", "나스닥").joinToString("\t"),
            listOf("2026-07-04", "1000000", "1100000", "9000", "7500", "26000").joinToString("\t"),
        ).joinToString("\n")

        val result = viewModel.parsePasteText(text)

        assertEquals(1, result.size)
        assertEquals("2026-07-04", result.single().benchmark?.date)
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
