package com.jkapp.finance.investment

import java.math.BigDecimal

data class ParsedInvestmentRow(
    val item: InvestmentItem?,
    val error: String?,
    val rawLine: String,
)

private data class InvestmentColumn(val label: String, val aliases: Set<String>)

// 시트에는 비중/리밸런싱/평가손익 등 파생 계산 열이 여러 개 섞여 있고 순서도 시트마다 달라질 수 있어,
// 고정 열 위치 대신 헤더 행의 이름으로 필요한 7개 열만 찾아 사용한다(BenchmarkSheetPaste와 동일한 방식).
// 매수단가는 매수금액/보유수량으로 계산해낼 수 있어 붙여넣기 대상에서 제외한다. 매수금액은 원화·달러
// 표기가 섞여 있을 수 있어, 1주 가격과 마찬가지로 셀에 $ 표시가 있는지로 통화를 함께 인식한다.
private val INVESTMENT_COLUMNS = listOf(
    InvestmentColumn("계좌", setOf("계좌", "이름")),
    InvestmentColumn("카테고리", setOf("카테고리")),
    InvestmentColumn("투자 종목", setOf("투자종목")),
    InvestmentColumn("1주 가격", setOf("1주가격")),
    InvestmentColumn("평가 금액(원화)", setOf("평가금액(원화)", "평가금액", "평가 금액")),
    InvestmentColumn("보유수량", setOf("보유수량")),
    InvestmentColumn("매수금액", setOf("매수금액")),
).map { column -> column.copy(aliases = column.aliases.mapTo(mutableSetOf()) { it.normalizeInvestmentHeaderCell() }) }

private val AMOUNT_COLUMN_LABELS = listOf("1주 가격", "평가 금액(원화)", "보유수량", "매수금액")

// "계" 행은 계좌 그룹의 소계/합계이며 개별 종목이 아니므로 가져오지 않는다.
private const val TOTAL_ROW_MARKER = "계"

// 통화 기호·콤마·공백 등 잡음 문자에 기대지 않고 숫자와 소수점만 뽑아 재조합한다.
private val AMOUNT_DIGITS_PATTERN = Regex("[0-9.]")

// 구글 스프레드시트에서 복사한 투자 종목 표(첫 줄은 헤더)를 파싱한다. owner는 붙여넣기 다이얼로그에서
// 선택한 명의로, 파싱된 모든 종목에 공통으로 적용된다(시트 자체에는 명의 열이 없다).
fun parseInvestmentSheetPaste(text: String, owner: String): List<ParsedInvestmentRow> {
    val lines = text.lines().filter { it.isNotBlank() }
    if (lines.size < 2) return emptyList()

    val header = lines.first().split('\t').map { it.normalizeInvestmentHeaderCell() }
    val columnIndexes = INVESTMENT_COLUMNS.associate { column ->
        column.label to header.indexOfFirst { cell -> cell in column.aliases }
    }
    val rawDataLines = lines.drop(1)

    val missingLabels = columnIndexes.filterValues { it < 0 }.keys
    if (missingLabels.isNotEmpty()) {
        val message = "헤더에서 다음 열을 찾을 수 없습니다: ${missingLabels.joinToString(", ")}"
        return rawDataLines.map { line -> ParsedInvestmentRow(item = null, error = message, rawLine = line) }
    }

    val assetNameIndex = columnIndexes.getValue("계좌")
    val dataLines = rawDataLines.filterNot { line -> line.split('\t').getOrNull(assetNameIndex)?.trim() == TOTAL_ROW_MARKER }

    val parsedRows = dataLines.map { line -> parseInvestmentRow(line, columnIndexes, owner) }

    // 같은 붙여넣기 안에서 (계좌, 카테고리, 투자종목) 조합이 중복되면 어느 값이 맞는지 알 수 없으므로
    // 자동으로 하나를 고르지 않고 에러로 표시한다(owner는 파싱 단위 전체에 공통이라 키에서 제외).
    val duplicateKeys = parsedRows.mapNotNull { it.item?.let { item -> Triple(item.assetName, item.category, item.investmentName) } }
        .groupingBy { it }.eachCount()
        .filterValues { it > 1 }
        .keys

    return parsedRows.map { row ->
        val key = row.item?.let { Triple(it.assetName, it.category, it.investmentName) }
        if (key != null && key in duplicateKeys) {
            ParsedInvestmentRow(item = null, error = "같은 붙여넣기 안에 계좌·카테고리·투자종목이 중복되었습니다", rawLine = row.rawLine)
        } else {
            row
        }
    }
}

// 헤더 셀 이름을 비교할 때 공백 차이와 대소문자 차이를 무시한다(BenchmarkSheetPaste와 동일한 정규화).
private fun String.normalizeInvestmentHeaderCell(): String = trim().replace(Regex("\\s+"), "").uppercase()

private fun parseInvestmentRow(line: String, columnIndexes: Map<String, Int>, owner: String): ParsedInvestmentRow {
    val cells = line.split('\t').map { it.trim() }
    val maxIndex = columnIndexes.values.max()
    if (cells.size <= maxIndex) {
        return ParsedInvestmentRow(item = null, error = "컬럼 수가 부족합니다 (${cells.size}/${maxIndex + 1})", rawLine = line)
    }

    val assetName = cells[columnIndexes.getValue("계좌")]
    val category = cells[columnIndexes.getValue("카테고리")]
    val investmentName = cells[columnIndexes.getValue("투자 종목")]
    if (assetName.isBlank()) return ParsedInvestmentRow(item = null, error = "계좌 값이 비어 있습니다", rawLine = line)
    if (category.isBlank()) return ParsedInvestmentRow(item = null, error = "카테고리 값이 비어 있습니다", rawLine = line)
    if (investmentName.isBlank()) return ParsedInvestmentRow(item = null, error = "투자 종목 값이 비어 있습니다", rawLine = line)

    val amounts = mutableMapOf<String, BigDecimal>()
    var purchaseAmountCurrency = "KRW"
    for (label in AMOUNT_COLUMN_LABELS) {
        val raw = cells[columnIndexes.getValue(label)]
        when (val result = parseInvestmentAmountCell(raw)) {
            is InvestmentAmountParseResult.Value -> {
                amounts[label] = result.amount
                if (label == "매수금액" && result.isUsd) purchaseAmountCurrency = "USD"
            }
            InvestmentAmountParseResult.Blank -> return ParsedInvestmentRow(item = null, error = "$label 값이 비어 있습니다", rawLine = line)
            InvestmentAmountParseResult.Invalid -> return ParsedInvestmentRow(
                item = null,
                error = "$label 값을 숫자로 변환할 수 없습니다: $raw",
                rawLine = line,
            )
        }
    }

    return ParsedInvestmentRow(
        item = InvestmentItem(
            assetName = assetName,
            category = category,
            investmentName = investmentName,
            pricePerShare = amounts.getValue("1주 가격"),
            valuationAmount = amounts.getValue("평가 금액(원화)"),
            quantity = amounts.getValue("보유수량"),
            purchaseAmount = CurrencyAmount(currency = purchaseAmountCurrency, amount = amounts.getValue("매수금액")),
        ),
        error = null,
        rawLine = line,
    )
}

private sealed interface InvestmentAmountParseResult {
    data object Blank : InvestmentAmountParseResult
    data class Value(val amount: BigDecimal, val isUsd: Boolean) : InvestmentAmountParseResult
    data object Invalid : InvestmentAmountParseResult
}

private fun parseInvestmentAmountCell(raw: String): InvestmentAmountParseResult {
    val digitsAndDot = AMOUNT_DIGITS_PATTERN.findAll(raw).joinToString("") { it.value }
    if (digitsAndDot.isEmpty()) {
        val looksLikeText = raw.any { it.isLetter() }
        return if (looksLikeText) InvestmentAmountParseResult.Invalid else InvestmentAmountParseResult.Blank
    }
    val unsigned = digitsAndDot.toBigDecimalOrNull() ?: return InvestmentAmountParseResult.Invalid
    val amount = if (raw.contains('-')) unsigned.negate() else unsigned
    return InvestmentAmountParseResult.Value(amount, isUsd = raw.contains('$'))
}
