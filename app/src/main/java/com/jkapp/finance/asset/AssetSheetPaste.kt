package com.jkapp.finance.asset

import java.math.BigDecimal

data class ParsedAssetRow(
    val item: AssetItem?,
    val error: String?,
    val rawLine: String,
)

private val OWNER_CODE_MAP = mapOf(
    "J" to "전지훈",
    "K" to "권유경",
)

// 시트 열 순서: 이름/명의/계좌/계좌번호/카드/금액. 카드 열은 사용하지 않는다.
private const val ASSET_COLUMN_COUNT = 6
private const val NAME_INDEX = 0
private const val OWNER_INDEX = 1
private const val INSTITUTION_INDEX = 2
private const val ACCOUNT_NUMBER_INDEX = 3
private const val AMOUNT_INDEX = 5

private sealed interface AmountParseResult {
    data object Blank : AmountParseResult
    data class Value(val amount: BigDecimal) : AmountParseResult
    data object Invalid : AmountParseResult
}

// 구글 시트 API가 돌려준 셀 행 목록(0번째 = 헤더 행)을 파싱한다.
// 열 순서는 고정(이름/명의/계좌/계좌번호/카드/금액)이며, 카드 열은 사용하지 않는다.
fun parseAssetRows(rows: List<List<String>>): List<ParsedAssetRow> {
    // 모든 셀이 비어 있는 행(시트 하단의 빈 행 등)은 무시한다.
    val nonBlankRows = rows.filter { row -> row.any { it.isNotBlank() } }
    // 헤더만 있거나 완전히 비어 있으면 데이터가 없는 것으로 본다.
    if (nonBlankRows.size < 2) return emptyList()

    val dataRows = nonBlankRows.drop(1)
    val parsedRows = dataRows.map { cells -> parseRow(cells) }

    // (이름, 명의) 조합이 같은 입력 안에서 중복되면 어느 값이 맞는지 알 수 없으므로
    // 자동으로 하나를 고르지 않고 에러로 표시한다. 이름이 같아도 명의가 다르면 별개의 자산이다.
    val duplicateKeys = parsedRows.mapNotNull { it.item?.let { item -> item.name to item.owner } }
        .groupingBy { it }.eachCount()
        .filterValues { it > 1 }
        .keys

    return parsedRows.map { row ->
        val key = row.item?.let { it.name to it.owner }
        if (key != null && key in duplicateKeys) {
            ParsedAssetRow(
                item = null,
                error = "같은 입력 안에 이름과 명의가 중복되었습니다: ${key.first} (${key.second})",
                rawLine = row.rawLine,
            )
        } else {
            row
        }
    }
}

private fun parseRow(rawCells: List<String>): ParsedAssetRow {
    val rawLine = rawCells.toRawLine()
    val cells = rawCells.map { it.trim() }
    // 시트 API는 뒤쪽 빈 셀을 생략해 보내므로, 필요한 열까지 빈 문자열로 채워 위치를 맞춘다.
    // 이렇게 하면 값이 없는 필드는 "컬럼 수 부족"이 아니라 "이름 비어 있음/금액 없음"으로 더 정확히 처리된다.
    val paddedCells = if (cells.size < ASSET_COLUMN_COUNT) {
        cells + List(ASSET_COLUMN_COUNT - cells.size) { "" }
    } else {
        cells
    }

    return when {
        paddedCells[NAME_INDEX].isBlank() -> ParsedAssetRow(
            item = null,
            error = "이름이 비어 있습니다",
            rawLine = rawLine,
        )
        else -> when (val amountResult = parseWonAmount(paddedCells[AMOUNT_INDEX])) {
            is AmountParseResult.Invalid -> ParsedAssetRow(
                item = null,
                error = "금액을 숫자로 변환할 수 없습니다: ${paddedCells[AMOUNT_INDEX]}",
                rawLine = rawLine,
            )
            is AmountParseResult.Blank -> paddedCells.toAssetRow(rawLine, amount = null)
            is AmountParseResult.Value -> paddedCells.toAssetRow(rawLine, amount = amountResult.amount)
        }
    }
}

// 오류 메시지에 원본을 보여줄 때 쓰는 표시용 문자열. 셀을 탭으로 잇는다.
private fun List<String>.toRawLine(): String = joinToString("\t")

private fun List<String>.toAssetRow(rawLine: String, amount: BigDecimal?): ParsedAssetRow = ParsedAssetRow(
    item = AssetItem(
        name = this[NAME_INDEX],
        owner = mapOwnerCode(this[OWNER_INDEX]),
        institution = this[INSTITUTION_INDEX].blankOrDashToNull(),
        accountNumber = this[ACCOUNT_NUMBER_INDEX].blankOrDashToNull(),
        card = null,
        amount = amount,
        hidden = this[NAME_INDEX] in DEFAULT_HIDDEN_ASSET_NAMES,
    ),
    error = null,
    rawLine = rawLine,
)

// J는 전지훈, K는 권유경으로 매핑하고, 그 외의 값(빈 값, "-", 오타, 전체 이름 등)은 모두 공동으로 처리한다.
private fun mapOwnerCode(code: String): String =
    OWNER_CODE_MAP[code.trim().uppercase()] ?: "공동"

// 통화 기호(₩, 유사 전각문자 등)·괄호(회계 표기)·콤마·공백이 정확히 어떤 문자인지 나열해서
// 제거하는 대신, 숫자(0-9)와 소수점만 뽑아 재조합한다. 잡음 문자 목록에 기대지 않으므로
// 예상치 못한 통화 기호/공백 변형이 섞여도 안정적으로 동작한다. 괄호(회계상 음수 표기)도
// 부호로 해석하지 않는다 — 이 데이터에서 "-"는 항상 "값 없음"을 의미할 뿐 실제 음수가 아니다.
private val AMOUNT_DIGITS_PATTERN = Regex("[0-9.]")

private fun parseWonAmount(raw: String): AmountParseResult {
    val digitsAndDot = AMOUNT_DIGITS_PATTERN.findAll(raw).joinToString("") { it.value }
    if (digitsAndDot.isEmpty()) {
        // 숫자가 전혀 없을 때, 글자(한글/영문 등)가 섞여 있으면 오타/미기입 텍스트로 보고 에러 처리하고,
        // 기호(-, ₩, 괄호, 공백 등)만 있으면 "값 없음"으로 처리한다.
        val looksLikeText = raw.any { it.isLetter() }
        return if (looksLikeText) AmountParseResult.Invalid else AmountParseResult.Blank
    }
    val amount = digitsAndDot.toBigDecimalOrNull() ?: return AmountParseResult.Invalid
    return AmountParseResult.Value(amount)
}

private fun String.blankOrDashToNull(): String? =
    trim().takeIf { it.isNotBlank() && it != "-" }
