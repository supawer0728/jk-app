package com.jkapp.finance.asset

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetSheetPasteTest {

    private val header = listOf("이름", "명의", "계좌", "계좌번호", "카드", "금액")

    private fun row(vararg cells: String): List<String> = cells.toList()

    // 시트에서 읽어온 것처럼 헤더 행을 앞에 붙여 파싱한다(첫 행은 항상 헤더로 취급되어 제외된다).
    private fun parse(vararg dataRows: List<String>): List<ParsedAssetRow> =
        parseAssetRows(listOf(header) + dataRows)

    @Test
    fun `첫 행은 헤더로 보고 제외한 뒤 데이터 행만 파싱한다`() {
        val result = parse(
            row("공용 계좌", "K", "토스뱅크", "1002-3212-1520", "-", "₩ 3,000,000"),
        )

        assertEquals(1, result.size)
        val item = result.single().item!!
        assertEquals("공용 계좌", item.name)
        assertEquals("권유경", item.owner)
        assertEquals("토스뱅크", item.institution)
        assertEquals("1002-3212-1520", item.accountNumber)
        assertNull(item.card)
        assertEquals(BigDecimal("3000000"), item.amount)
    }

    @Test
    fun `헤더만 있고 데이터 행이 없으면 빈 목록을 반환한다`() {
        assertEquals(emptyList<ParsedAssetRow>(), parseAssetRows(listOf(header)))
        assertEquals(emptyList<ParsedAssetRow>(), parseAssetRows(emptyList()))
    }

    @Test
    fun `명의 코드 J와 K를 각각 전지훈과 권유경으로 매핑한다`() {
        val result = parse(
            row("자산A", "J", "-", "-", "-", "-"),
            row("자산B", "K", "-", "-", "-", "-"),
        )

        assertEquals("전지훈", result[0].item?.owner)
        assertEquals("권유경", result[1].item?.owner)
    }

    @Test
    fun `명의 코드는 대소문자 구분 없이 매핑한다`() {
        val result = parse(
            row("자산A", "j", "-", "-", "-", "-"),
            row("자산B", "k", "-", "-", "-", "-"),
        )

        assertEquals("전지훈", result[0].item?.owner)
        assertEquals("권유경", result[1].item?.owner)
    }

    @Test
    fun `J K 이외의 명의 값은 모두 공동으로 매핑한다`() {
        val result = parse(
            row("자산A", "본인", "-", "-", "-", "-"),
            row("자산B", "전지훈", "-", "-", "-", "-"),
        )

        assertEquals("공동", result[0].item?.owner)
        assertEquals("공동", result[1].item?.owner)
    }

    @Test
    fun `명의가 대시이거나 비어있으면 공동으로 매핑한다`() {
        val result = parse(
            row("전세보증금", "-", "-", "-", "-", "₩ -"),
            row("전세보증금2", "", "-", "-", "-", "-"),
        )

        assertEquals("공동", result[0].item?.owner)
        assertEquals("공동", result[1].item?.owner)
        assertNull(result[0].item?.amount)
    }

    @Test
    fun `계좌와 계좌번호가 대시면 null로 처리한다`() {
        val result = parse(row("적금", "K", "-", "-", "-", "₩ -"))

        val item = result.single().item!!
        assertNull(item.institution)
        assertNull(item.accountNumber)
        assertNull(item.amount)
    }

    @Test
    fun `금액에서 원화기호와 콤마를 제거하고 파싱한다`() {
        val result = parse(row("종합계좌", "J", "삼성증권", "7113425852-01", "-", "₩ 106,832,390"))

        assertEquals(BigDecimal("106832390"), result.single().item?.amount)
    }

    @Test
    fun `괄호로 감싼 회계 표기 금액은 음수가 아닌 양수로 파싱한다`() {
        val result = parse(row("퇴직연금(DC)", "K", "미래에셋증권", "-", "-", "(₩ 23,779,706)"))

        val parsed = result.single()
        assertEquals(BigDecimal("23779706"), parsed.item?.amount)
        assertEquals(null, parsed.error)
    }

    @Test
    fun `괄호로 감싼 금액은 은행 콤보와 계좌번호가 있어도 정상 파싱한다`() {
        val result = parse(row("공용 계좌", "K", "토스뱅크", "1002-3212-1520", "-", "(₩ 3,000,000)"))

        val parsed = result.single()
        assertEquals(BigDecimal("3000000"), parsed.item?.amount)
        assertEquals(null, parsed.error)
    }

    @Test
    fun `대시 부호는 음수가 아닌 값 없음으로 취급한다`() {
        val result = parse(row("마이너스표기", "J", "-", "-", "-", "-3,000,000"))

        assertEquals(BigDecimal("3000000"), result.single().item?.amount)
    }

    @Test
    fun `계좌번호와 은행명에 숫자가 섞여 있어도 금액 칸만 정확히 파싱한다`() {
        val result = parse(row("해외계좌", "J", "삼성증권", "7131769648-01", "-", "(₩ 19,304,122)"))

        val parsed = result.single()
        assertEquals(BigDecimal("19304122"), parsed.item?.amount)
        assertEquals(null, parsed.error)
    }

    @Test
    fun `카드 열은 무시한다`() {
        val result = parse(row("K 용돈", "K", "카카오뱅크", "3333095726187", "현대/카카오", "₩ 1,100,000"))

        assertNull(result.single().item?.card)
    }

    @Test
    fun `모든 셀이 비어 있는 행은 무시한다`() {
        val result = parse(
            row("자산A", "J", "-", "-", "-", "-"),
            emptyList(),
            row("", "", "", "", "", ""),
            row("자산B", "K", "-", "-", "-", "-"),
        )

        assertEquals(2, result.size)
    }

    @Test
    fun `뒤쪽 열이 생략된 행도 빈 값으로 채워 파싱한다`() {
        // 시트 API는 값이 없는 뒤쪽 셀을 생략해 보낸다(이름 · 명의만 있는 행).
        val result = parse(row("이름만", "J"))

        val parsed = result.single()
        assertEquals("이름만", parsed.item?.name)
        assertEquals("전지훈", parsed.item?.owner)
        assertNull(parsed.item?.institution)
        assertNull(parsed.item?.amount)
        assertNull(parsed.error)
    }

    @Test
    fun `이름이 비어있으면 에러로 처리한다`() {
        val result = parse(row("", "J", "-", "-", "-", "-"))

        val parsed = result.single()
        assertNull(parsed.item)
        assertTrue(parsed.error!!.contains("이름이 비어 있습니다"))
    }

    @Test
    fun `금액을 숫자로 변환할 수 없으면 null로 조용히 넘기지 않고 에러로 처리한다`() {
        val result = parse(row("현금", "J", "-", "-", "-", "미정"))

        val parsed = result.single()
        assertNull(parsed.item)
        assertTrue(parsed.error!!.contains("금액을 숫자로 변환할 수 없습니다"))
    }

    @Test
    fun `같은 입력 안에 이름과 명의가 모두 중복되면 두 행 모두 에러로 처리한다`() {
        val result = parse(
            row("현금", "J", "-", "-", "-", "₩ 100"),
            row("현금", "J", "-", "-", "-", "₩ 200"),
            row("주식", "J", "-", "-", "-", "₩ 300"),
        )

        assertNull(result[0].item)
        assertTrue(result[0].error!!.contains("중복"))
        assertNull(result[1].item)
        assertTrue(result[1].error!!.contains("중복"))
        assertEquals("주식", result[2].item?.name)
    }

    @Test
    fun `기본 숨김 목록에 있는 이름은 hidden이 true로 파싱된다`() {
        val result = parse(
            row("공용 계좌", "K", "토스뱅크", "-", "-", "₩ 3,000,000"),
            row("현금", "K", "-", "-", "-", "₩ 1,000"),
        )

        assertEquals(true, result[0].item?.hidden)
        assertEquals(false, result[1].item?.hidden)
    }

    @Test
    fun `이름이 같아도 명의가 다르면 중복이 아니다`() {
        val result = parse(
            row("적금", "J", "-", "-", "-", "₩ 100"),
            row("적금", "K", "-", "-", "-", "₩ 200"),
        )

        assertEquals("전지훈", result[0].item?.owner)
        assertEquals(BigDecimal("100"), result[0].item?.amount)
        assertEquals("권유경", result[1].item?.owner)
        assertEquals(BigDecimal("200"), result[1].item?.amount)
    }
}
