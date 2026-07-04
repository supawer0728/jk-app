package com.jkapp.ui

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
// DiaryViewModel.todayDate()는 시스템 시간대를 사용하므로 UTC±12h 경계 조건에서
// 하루 차이가 날 수 있다. 이는 의도적인 tradeoff다.
private fun isoDateToUtcEpochMillis(isoDate: String?): Long =
    runCatching {
        LocalDate.parse(isoDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }.getOrElse { System.currentTimeMillis() }

private fun Long.toUtcIsoDate(): String =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)

// yyyy-MM-dd 형식 날짜를 UTC 기준으로 선택하는 공용 DatePickerDialog.
// AssetScreen과 DiaryFormScreen이 동일한 UTC 변환 로직을 각자 구현하던 것을 통합했다.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IsoDatePickerDialog(
    initialDate: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val initialMillis = remember(initialDate) { isoDateToUtcEpochMillis(initialDate) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { millis -> onConfirm(millis.toUtcIsoDate()) } ?: onDismiss()
            }) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}
