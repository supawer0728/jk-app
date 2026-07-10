package com.jkapp.common

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// DatePickerState.selectedDateMillis는 UTC 자정 기준 epoch millis를 반환하므로
// 초기값 변환과 확인 버튼 변환 모두 ZoneOffset.UTC로 통일한다.
// todayDate()는 시스템 시간대를 사용하므로 UTC±12h 경계 조건에서
// 하루 차이가 날 수 있다. 이는 의도적인 tradeoff다.
private fun isoDateToUtcEpochMillis(isoDate: String?): Long =
    runCatching {
        LocalDate.parse(isoDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }.getOrElse { System.currentTimeMillis() }

private fun Long.toUtcIsoDate(): String =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)

// 오늘(시스템 시간대 기준)까지만 선택 가능하게 해 미래 날짜 입력을 막는 SelectableDates.
// 날짜 판정은 순수 함수 isNotAfterUtcDay로 위임해 단위 테스트로 검증한다(위 UTC tradeoff 참고).
@OptIn(ExperimentalMaterial3Api::class)
private fun notFutureSelectableDates(): SelectableDates {
    val today = todayDate()
    val todayYear = LocalDate.parse(today, DateTimeFormatter.ISO_LOCAL_DATE).year
    return object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long): Boolean =
            isNotAfterUtcDay(utcTimeMillis, today)
        override fun isSelectableYear(year: Int): Boolean = year <= todayYear
    }
}

// yyyy-MM-dd 형식 날짜를 UTC 기준으로 선택하는 공용 DatePickerDialog.
// AssetScreen과 DiaryFormScreen이 동일한 UTC 변환 로직을 각자 구현하던 것을 통합했다.
// allowFutureDates=false면 오늘 이후(미래) 날짜를 선택할 수 없다(자산/투자종목/벤치마크 입력에서 사용).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IsoDatePickerDialog(
    initialDate: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    allowFutureDates: Boolean = true,
) {
    val initialMillis = remember(initialDate) { isoDateToUtcEpochMillis(initialDate) }
    val selectableDates = remember(allowFutureDates) {
        if (allowFutureDates) DatePickerDefaults.AllDates else notFutureSelectableDates()
    }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        selectableDates = selectableDates,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            // selectableDates가 미래 날짜 탭을 막아도, 편집 대상이 이미 미래 날짜면(initialDate가 미래)
            // 그 값이 그대로 선택된 채 확인될 수 있다. 미래 날짜가 저장되지 않도록 확인 버튼 자체를 막는다.
            val selectedMillis = datePickerState.selectedDateMillis
            val canConfirm = selectedMillis != null && (allowFutureDates || isNotAfterUtcDay(selectedMillis))
            TextButton(
                onClick = { selectedMillis?.let { onConfirm(it.toUtcIsoDate()) } },
                enabled = canConfirm,
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}
