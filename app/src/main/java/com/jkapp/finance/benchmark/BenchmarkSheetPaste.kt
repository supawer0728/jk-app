package com.jkapp.finance.benchmark

import java.math.BigDecimal

data class ParsedBenchmarkRow(
    val benchmark: Benchmark?,
    val error: String?,
    val rawLine: String,
)

private data class BenchmarkColumn(val label: String, val aliases: Set<String>)

// 시트에는 수익률/전월대비/MDD 등 파생 계산 열이 여러 개 섞여 있고 순서도 시트마다 달라질 수 있어,
// 고정 열 위치 대신 헤더 행의 이름으로 필요한 7개 열만 찾아 사용한다.
// "총계"/"합계"처럼 지나치게 넓은 별칭은 다른 용도의 합계 열을 잘못 매칭할 수 있어 제외하고,
// 실제 시트에서 쓰이는 이름("현재금액", "계")만 별칭으로 둔다.
private val BENCHMARK_COLUMNS = listOf(
    BenchmarkColumn("날짜", setOf("날짜")),
    BenchmarkColumn("추가투자", setOf("추가투자")),
    BenchmarkColumn("현재금액", setOf("현재금액", "계")),
    BenchmarkColumn("KOSPI", setOf("KOSPI")),
    BenchmarkColumn("S&P500", setOf("S&P500", "SNP500")),
    BenchmarkColumn("나스닥", setOf("나스닥")),
).map { column -> column.copy(aliases = column.aliases.mapTo(mutableSetOf()) { it.normalizeHeaderCell() }) }

private val AMOUNT_COLUMN_LABELS = listOf("추가투자", "현재금액", "KOSPI", "S&P500", "나스닥")

private val ISO_DATE_REGEX = Regex("""\d{4}-\d{2}-\d{2}""")

// 통화 기호·콤마·공백 등 잡음 문자에 기대지 않고 숫자와 소수점만 뽑아 재조합한다.
// 부호(-)는 별도로 감지해 재조합 후 다시 적용한다(음수는 출금을 의미하므로 보존해야 한다).
private val AMOUNT_DIGITS_PATTERN = Regex("[0-9.]")

// 구글 스프레드시트에서 복사한 벤치마크 표(첫 줄은 헤더)를 파싱한다.
fun parseBenchmarkSheetPaste(text: String): List<ParsedBenchmarkRow> {
    val lines = text.lines().filter { it.isNotBlank() }
    if (lines.size < 2) return emptyList()

    val header = lines.first().split('\t').map { it.normalizeHeaderCell() }
    val columnIndexes = BENCHMARK_COLUMNS.associate { column ->
        column.label to header.indexOfFirst { cell -> cell in column.aliases }
    }
    val dataLines = lines.drop(1)

    val missingLabels = columnIndexes.filterValues { it < 0 }.keys
    if (missingLabels.isNotEmpty()) {
        val message = "헤더에서 다음 열을 찾을 수 없습니다: ${missingLabels.joinToString(", ")}"
        return dataLines.map { line -> ParsedBenchmarkRow(benchmark = null, error = message, rawLine = line) }
    }

    val parsedRows = dataLines.map { line -> parseBenchmarkRow(line, columnIndexes) }

    // 같은 붙여넣기 안에 같은 날짜가 중복되면 어느 값이 맞는지 알 수 없으므로 자동으로 하나를
    // 고르지 않고 에러로 표시한다.
    val duplicateDates = parsedRows.mapNotNull { it.benchmark?.date }
        .groupingBy { it }.eachCount()
        .filterValues { it > 1 }
        .keys

    return parsedRows.map { row ->
        val date = row.benchmark?.date
        if (date != null && date in duplicateDates) {
            ParsedBenchmarkRow(benchmark = null, error = "같은 붙여넣기 안에 날짜가 중복되었습니다: $date", rawLine = row.rawLine)
        } else {
            row
        }
    }
}

// 헤더 셀 이름을 비교할 때 공백 차이(예: "KOSPI 상승률" vs "KOSPI상승률")와 대소문자 차이를
// 무시하되, "KOSPI"와 "KOSPI 상승률"처럼 접두사만 같은 열은 여전히 다른 값으로 남겨 서로 섞이지 않게 한다.
private fun String.normalizeHeaderCell(): String = trim().replace(Regex("\\s+"), "").uppercase()

private fun parseBenchmarkRow(line: String, columnIndexes: Map<String, Int>): ParsedBenchmarkRow {
    val cells = line.split('\t').map { it.trim() }
    val maxIndex = columnIndexes.values.max()
    if (cells.size <= maxIndex) {
        return ParsedBenchmarkRow(
            benchmark = null,
            error = "컬럼 수가 부족합니다 (${cells.size}/${maxIndex + 1})",
            rawLine = line,
        )
    }

    val date = cells[columnIndexes.getValue("날짜")]
    if (!ISO_DATE_REGEX.matches(date)) {
        return ParsedBenchmarkRow(benchmark = null, error = "날짜 형식이 올바르지 않습니다(yyyy-MM-dd): $date", rawLine = line)
    }

    val amounts = mutableMapOf<String, BigDecimal>()
    for (label in AMOUNT_COLUMN_LABELS) {
        val raw = cells[columnIndexes.getValue(label)]
        val amount = when (val result = parseAmountCell(raw)) {
            is BenchmarkAmountParseResult.Value -> result.amount
            // 추가투자는 "이번 달에 추가 납입이 없었다"는 뜻으로 비어 있을 수 있으므로 0으로 취급한다.
            // 나머지 필드는 모델상 필수 값이라 비어 있으면 에러로 처리한다.
            BenchmarkAmountParseResult.Blank -> if (label == "추가투자") {
                BigDecimal.ZERO
            } else {
                return ParsedBenchmarkRow(benchmark = null, error = "$label 값이 비어 있습니다", rawLine = line)
            }
            BenchmarkAmountParseResult.Invalid -> return ParsedBenchmarkRow(
                benchmark = null,
                error = "$label 값을 숫자로 변환할 수 없습니다: $raw",
                rawLine = line,
            )
        }
        amounts[label] = amount
    }

    return ParsedBenchmarkRow(
        benchmark = Benchmark(
            firestoreId = date,
            date = date,
            additionalInvestment = amounts.getValue("추가투자"),
            currentAmount = amounts.getValue("현재금액"),
            kospi = amounts.getValue("KOSPI"),
            snp500 = amounts.getValue("S&P500"),
            nasdaq = amounts.getValue("나스닥"),
        ),
        error = null,
        rawLine = line,
    )
}

private sealed interface BenchmarkAmountParseResult {
    data object Blank : BenchmarkAmountParseResult
    data class Value(val amount: BigDecimal) : BenchmarkAmountParseResult
    data object Invalid : BenchmarkAmountParseResult
}

private fun parseAmountCell(raw: String): BenchmarkAmountParseResult {
    val digitsAndDot = AMOUNT_DIGITS_PATTERN.findAll(raw).joinToString("") { it.value }
    if (digitsAndDot.isEmpty()) {
        // 숫자가 전혀 없을 때, 글자가 섞여 있으면 오타/미기입 텍스트로 보고 에러 처리하고,
        // 기호(-, ₩, 공백 등)만 있거나 완전히 비어 있으면 "값 없음"으로 처리한다.
        val looksLikeText = raw.any { it.isLetter() }
        return if (looksLikeText) BenchmarkAmountParseResult.Invalid else BenchmarkAmountParseResult.Blank
    }
    val unsigned = digitsAndDot.toBigDecimalOrNull() ?: return BenchmarkAmountParseResult.Invalid
    val amount = if (raw.contains('-')) unsigned.negate() else unsigned
    return BenchmarkAmountParseResult.Value(amount)
}
