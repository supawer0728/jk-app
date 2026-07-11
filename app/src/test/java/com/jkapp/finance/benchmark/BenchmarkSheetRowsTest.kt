package com.jkapp.finance.benchmark

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// 시트 API가 돌려주는 셀 행(List<List<String>>) 파서(parseBenchmarkRows)를 검증한다.
// 필요한 6개 열(날짜·추가투자·현재금액·KOSPI·S&P500·나스닥)만 헤더 이름으로 찾아 쓴다.
class BenchmarkSheetRowsTest {

    private val header = listOf("날짜", "추가투자", "현재금액", "KOSPI", "S&P500", "나스닥")

    // 실제 시트처럼 파생 계산 열(원금/수익/수익률/전월대비/MDD 등)이 섞여 있는 헤더.
    private val fullHeader = listOf(
        "날짜", "추가투자", "원금", "수익", "수익률", "계", "", "벤치마킹/날짜",
        "KOSPI", "KOSPI 상승률", "KOSPI전월대비",
        "S&P500", "S&P500 상승률", "S&P500 전월대비",
        "나스닥", "나스닥 상승률", "나스닥 전월대비",
        "자산 수익률", "자산 전월대비",
        "KOSPI MDD", "S&P500 MDD", "나스닥 MDD", "자산 MDD",
    )

    private fun fullDataRow(
        date: String = "2026-07-03",
        additionalInvestment: String = "₩0",
        currentAmount: String = "₩576,968,966",
        kospi: String = "8930.3",
        snp500: String = "7358.22",
        nasdaq: String = "25476.64",
    ) = listOf(
        date, additionalInvestment, "₩364,500,000", "₩212,468,966", "58.29%", currentAmount, "", date,
        kospi, "192.89%", "0.00%",
        snp500, "60.84%", "0.00%",
        nasdaq, "67.22%", "0.00%",
        "58.29%", "-0.06%",
        "0.00%", "-1.54%", "-3.29%", "-0.58%",
    )

    @Test
    fun `헤더와 데이터 행을 파싱한다`() {
        val rows = listOf(
            header,
            listOf("2026-07-04", "1000000", "1100000", "9000", "7500", "26000"),
        )

        val benchmark = parseBenchmarkRows(rows).single().benchmark!!
        assertEquals("2026-07-04", benchmark.date)
        assertEquals(BigDecimal("1000000"), benchmark.additionalInvestment)
        assertEquals(BigDecimal("1100000"), benchmark.currentAmount)
        assertEquals(BigDecimal("26000"), benchmark.nasdaq)
    }

    @Test
    fun `파생 계산 열이 섞여 있어도 필요한 6개 열만 헤더 이름으로 찾아 파싱한다`() {
        val benchmark = parseBenchmarkRows(listOf(fullHeader, fullDataRow())).single().benchmark!!
        assertEquals("2026-07-03", benchmark.date)
        assertEquals(BigDecimal("0"), benchmark.additionalInvestment)
        assertEquals(BigDecimal("576968966"), benchmark.currentAmount)
        assertEquals(BigDecimal("8930.3"), benchmark.kospi)
        assertEquals(BigDecimal("7358.22"), benchmark.snp500)
        assertEquals(BigDecimal("25476.64"), benchmark.nasdaq)
    }

    @Test
    fun `KOSPI와 KOSPI 상승률처럼 접두사가 같은 열을 혼동하지 않는다`() {
        val result = parseBenchmarkRows(listOf(fullHeader, fullDataRow(kospi = "1111.1")))
        assertEquals(BigDecimal("1111.1"), result.single().benchmark?.kospi)
    }

    @Test
    fun `열 순서가 달라도 헤더 이름만 맞으면 정상 파싱한다`() {
        val rows = listOf(
            listOf("KOSPI", "나스닥", "S&P500", "날짜", "추가투자", "현재금액"),
            listOf("9000", "26000", "7500", "2026-07-04", "1000000", "1200000"),
        )

        val benchmark = parseBenchmarkRows(rows).single().benchmark!!
        assertEquals("2026-07-04", benchmark.date)
        assertEquals(BigDecimal("9000"), benchmark.kospi)
        assertEquals(BigDecimal("26000"), benchmark.nasdaq)
    }

    @Test
    fun `현재금액 열은 계라는 헤더 이름도 인식한다`() {
        val rows = listOf(
            listOf("날짜", "추가투자", "계", "KOSPI", "S&P500", "나스닥"),
            listOf("2026-07-04", "0", "1200000", "9000", "7500", "26000"),
        )
        assertEquals(BigDecimal("1200000"), parseBenchmarkRows(rows).single().benchmark?.currentAmount)
    }

    @Test
    fun `필요한 열이 헤더에 없으면 모든 데이터 행이 에러로 처리된다`() {
        val rows = listOf(
            listOf("날짜", "추가투자"),
            listOf("2026-07-04", "1000000"),
        )
        val parsed = parseBenchmarkRows(rows).single()
        assertNull(parsed.benchmark)
        assertTrue(parsed.error!!.contains("헤더에서"))
    }

    @Test
    fun `날짜 형식이 올바르지 않으면 에러로 처리한다`() {
        val parsed = parseBenchmarkRows(listOf(fullHeader, fullDataRow(date = "2026.07.03"))).single()
        assertNull(parsed.benchmark)
        assertTrue(parsed.error!!.contains("날짜 형식"))
    }

    @Test
    fun `숫자로 변환할 수 없는 값은 에러로 처리한다`() {
        val parsed = parseBenchmarkRows(listOf(fullHeader, fullDataRow(currentAmount = "미정"))).single()
        assertNull(parsed.benchmark)
        assertTrue(parsed.error!!.contains("현재금액 값을 숫자로 변환할 수 없습니다"))
    }

    @Test
    fun `추가투자가 비어 있으면 0으로 처리한다`() {
        val result = parseBenchmarkRows(listOf(fullHeader, fullDataRow(additionalInvestment = "-")))
        assertEquals(BigDecimal.ZERO, result.single().benchmark?.additionalInvestment)
    }

    @Test
    fun `음수 부호가 있는 금액은 부호를 보존해 파싱한다`() {
        val result = parseBenchmarkRows(listOf(fullHeader, fullDataRow(additionalInvestment = "-500,000")))
        assertEquals(BigDecimal("-500000"), result.single().benchmark?.additionalInvestment)
    }

    @Test
    fun `같은 입력 안에 날짜가 중복되면 두 행 모두 에러로 처리한다`() {
        val rows = listOf(fullHeader, fullDataRow(date = "2026-07-03"), fullDataRow(date = "2026-07-03"))
        val result = parseBenchmarkRows(rows)
        assertEquals(2, result.size)
        assertNull(result[0].benchmark)
        assertTrue(result[0].error!!.contains("중복"))
        assertNull(result[1].benchmark)
        assertTrue(result[1].error!!.contains("중복"))
    }

    @Test
    fun `뒤쪽 빈 셀이 생략된 행은 해당 필드가 비어 있다는 오류로 처리한다`() {
        // 시트 API는 뒤쪽 빈 셀을 생략하므로 나스닥(마지막 열)이 없는 짧은 행이 올 수 있다.
        val rows = listOf(
            header,
            listOf("2026-07-04", "0", "1100000", "9000", "7500"),
        )
        val parsed = parseBenchmarkRows(rows).single()
        assertNull(parsed.benchmark)
        assertTrue(parsed.error!!.contains("나스닥 값이 비어 있습니다"))
    }

    @Test
    fun `모든 셀이 빈 행은 무시한다`() {
        val rows = listOf(
            header,
            listOf("2026-07-04", "0", "1100000", "9000", "7500", "26000"),
            listOf("", "", "", "", "", ""),
        )
        val result = parseBenchmarkRows(rows)
        assertEquals(1, result.size)
        assertEquals("2026-07-04", result.single().benchmark?.date)
    }

    @Test
    fun `데이터 행이 없으면 빈 목록을 반환한다`() {
        assertEquals(emptyList<ParsedBenchmarkRow>(), parseBenchmarkRows(listOf(header)))
    }
}
