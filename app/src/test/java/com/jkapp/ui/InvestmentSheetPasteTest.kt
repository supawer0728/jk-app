package com.jkapp.ui

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InvestmentSheetPasteTest {

    private fun row(vararg cells: String) = cells.joinToString("\t")

    private val fullHeader = row(
        "계좌", "카테고리", "카테고리 목표 비중", "투자 종목", "1주 가격", "평가 금액(원화)",
        "비중", "목표 비중", "리밸런싱 목표", "리밸런싱 조정치", "주수 조정",
        "매수단가", "보유수량", "매수금액", "평가금액", "평가손익(현금 외)", "수익률(현금 외)",
    )

    private fun fullDataRow(
        assetName: String = "종합",
        category: String = "주식",
        investmentName: String = "KODEX 미국나스닥100(H)",
        pricePerShare: String = "₩22,250",
        valuationAmount: String = "₩106,800,000",
        quantity: String = "4,800",
        purchaseAmount: String = "₩108,384,000",
    ) = row(
        assetName, category, "70.00%", investmentName, pricePerShare, valuationAmount,
        "67.43%", "70.00%", "₩110,874,723", "₩4,074,723", "183",
        "₩22,580", quantity, purchaseAmount, "₩106,800,000", "-₩1,584,000", "-1.46%",
    )

    @Test
    fun `비중 리밸런싱 등 파생 열이 섞여 있어도 필요한 7개 열만 헤더 이름으로 찾아 파싱한다`() {
        val text = listOf(fullHeader, fullDataRow()).joinToString("\n")

        val result = parseInvestmentSheetPaste(text, owner = "전지훈")

        assertEquals(1, result.size)
        val item = result.single().item!!
        assertEquals("종합", item.assetName)
        assertEquals("주식", item.category)
        assertEquals("KODEX 미국나스닥100(H)", item.investmentName)
        assertEquals(BigDecimal("22250"), item.pricePerShare)
        assertEquals(BigDecimal("106800000"), item.valuationAmount)
        assertEquals(BigDecimal("4800"), item.quantity)
        assertEquals(BigDecimal("108384000"), item.purchaseAmount)
    }

    @Test
    fun `매수단가는 붙여넣기 대상이 아니므로 0으로 채워지고 계좌 이름 별칭도 인식한다`() {
        val header = row("이름", "카테고리", "투자 종목", "1주 가격", "평가 금액(원화)", "보유수량", "매수금액")
        val data = row("종합", "주식", "삼성전자", "₩70,000", "₩700,000", "10", "₩650,000")
        val text = listOf(header, data).joinToString("\n")

        val result = parseInvestmentSheetPaste(text, owner = "전지훈")

        val item = result.single().item!!
        assertEquals("KRW", item.purchasePrice.currency)
        assertEquals(BigDecimal.ZERO, item.purchasePrice.amount)
    }

    @Test
    fun `1주 가격이 달러면 매수단가 통화가 USD로 기본 설정된다`() {
        val text = listOf(
            fullHeader,
            fullDataRow(investmentName = "애플 AAPL", pricePerShare = "$308.6300", valuationAmount = "₩3,798,865", quantity = "8", purchaseAmount = "$2,503.3200"),
        ).joinToString("\n")

        val result = parseInvestmentSheetPaste(text, owner = "전지훈")

        val item = result.single().item!!
        assertEquals(BigDecimal("308.6300"), item.pricePerShare)
        assertEquals(BigDecimal("2503.3200"), item.purchaseAmount)
        assertEquals("USD", item.purchasePrice.currency)
    }

    @Test
    fun `열 순서가 달라도 헤더 이름만 맞으면 정상 파싱한다`() {
        val header = row("1주 가격", "매수금액", "계좌", "카테고리", "투자 종목", "보유수량", "평가 금액(원화)")
        val data = row("₩1000", "₩9000", "종합", "현금", "현금", "10", "₩10000")
        val text = listOf(header, data).joinToString("\n")

        val result = parseInvestmentSheetPaste(text, owner = "전지훈")

        val item = result.single().item!!
        assertEquals("종합", item.assetName)
        assertEquals(BigDecimal("1000"), item.pricePerShare)
        assertEquals(BigDecimal("10000"), item.valuationAmount)
        assertEquals(BigDecimal("10"), item.quantity)
        assertEquals(BigDecimal("9000"), item.purchaseAmount)
    }

    @Test
    fun `계 합계 행은 건너뛴다`() {
        val text = listOf(
            fullHeader,
            fullDataRow(),
            row("계", "", "100%", "-", "-", "₩177,676,520", "-", "100.00%", "-", "-", "-", "-", "-", "₩173,827,840", "₩177,676,520", "₩3,848,680", "2.21%"),
        ).joinToString("\n")

        val result = parseInvestmentSheetPaste(text, owner = "전지훈")

        assertEquals(1, result.size)
    }

    @Test
    fun `계좌 카테고리 투자종목이 비어 있으면 에러로 표시한다`() {
        val text = listOf(fullHeader, fullDataRow(investmentName = "")).joinToString("\n")

        val result = parseInvestmentSheetPaste(text, owner = "전지훈")

        assertNull(result.single().item)
        assertTrue(result.single().error!!.contains("투자 종목"))
    }

    @Test
    fun `금액 열이 숫자로 변환할 수 없으면 에러로 표시한다`() {
        val text = listOf(fullHeader, fullDataRow(pricePerShare = "가격미정")).joinToString("\n")

        val result = parseInvestmentSheetPaste(text, owner = "전지훈")

        assertNull(result.single().item)
        assertTrue(result.single().error!!.contains("1주 가격"))
    }

    @Test
    fun `필수 헤더가 없으면 모든 행이 에러로 표시된다`() {
        val header = row("계좌", "투자 종목", "1주 가격", "평가 금액(원화)", "보유수량", "매수금액")
        val text = listOf(header, fullDataRow()).joinToString("\n")

        val result = parseInvestmentSheetPaste(text, owner = "전지훈")

        assertNull(result.single().item)
        assertTrue(result.single().error!!.contains("카테고리"))
    }

    @Test
    fun `같은 붙여넣기 안에서 계좌 카테고리 투자종목이 중복되면 에러로 표시한다`() {
        val text = listOf(fullHeader, fullDataRow(), fullDataRow()).joinToString("\n")

        val result = parseInvestmentSheetPaste(text, owner = "전지훈")

        assertEquals(2, result.size)
        result.forEach { row ->
            assertNull(row.item)
            assertTrue(row.error!!.contains("중복"))
        }
    }

    @Test
    fun `빈 텍스트나 헤더만 있으면 빈 목록을 반환한다`() {
        assertEquals(emptyList<ParsedInvestmentRow>(), parseInvestmentSheetPaste("", owner = "전지훈"))
        assertEquals(emptyList<ParsedInvestmentRow>(), parseInvestmentSheetPaste(fullHeader, owner = "전지훈"))
    }
}
