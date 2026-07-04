package com.jkapp.ui

import com.jkapp.data.model.AssetItem
import com.jkapp.data.model.DEFAULT_HIDDEN_ASSET_NAMES
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

private sealed interface AmountParseResult {
    data object Blank : AmountParseResult
    data class Value(val amount: BigDecimal) : AmountParseResult
    data object Invalid : AmountParseResult
}

// 구글 스프레드시트에서 복사한 표(탭으로 구분된 텍스트, 열 순서: 이름/명의/계좌/계좌번호/카드/금액)를 파싱한다.
// 카드 열은 사용하지 않는다.
fun parseGoogleSheetPaste(text: String, hasHeader: Boolean): List<ParsedAssetRow> {
    val lines = text.lines()
    val dataLines = if (hasHeader) lines.drop(1) else lines

    val parsedRows = dataLines
        .filter { it.isNotBlank() }
        .map { line -> parseRow(line) }

    // (이름, 명의) 조합이 같은 붙여넣기 안에서 중복되면 어느 값이 맞는지 알 수 없으므로
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
                error = "같은 붙여넣기 안에 이름과 명의가 중복되었습니다: ${key.first} (${key.second})",
                rawLine = row.rawLine,
            )
        } else {
            row
        }
    }
}

private fun parseRow(line: String): ParsedAssetRow {
    val cells = line.split('\t').map { it.trim() }
    return when {
        cells.size < 6 -> ParsedAssetRow(
            item = null,
            error = "컬럼 수가 부족합니다 (${cells.size}/6)",
            rawLine = line,
        )
        cells[0].isBlank() -> ParsedAssetRow(
            item = null,
            error = "이름이 비어 있습니다",
            rawLine = line,
        )
        else -> when (val amountResult = parseWonAmount(cells[5])) {
            is AmountParseResult.Invalid -> ParsedAssetRow(
                item = null,
                error = "금액을 숫자로 변환할 수 없습니다: ${cells[5]}",
                rawLine = line,
            )
            is AmountParseResult.Blank -> line.toAssetRow(cells, amount = null)
            is AmountParseResult.Value -> line.toAssetRow(cells, amount = amountResult.amount)
        }
    }
}

private fun String.toAssetRow(cells: List<String>, amount: BigDecimal?): ParsedAssetRow = ParsedAssetRow(
    item = AssetItem(
        name = cells[0],
        owner = mapOwnerCode(cells[1]),
        institution = cells[2].blankOrDashToNull(),
        accountNumber = cells[3].blankOrDashToNull(),
        card = null,
        amount = amount,
        hidden = cells[0] in DEFAULT_HIDDEN_ASSET_NAMES,
    ),
    error = null,
    rawLine = this,
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
