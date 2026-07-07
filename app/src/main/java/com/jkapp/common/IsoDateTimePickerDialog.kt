package com.jkapp.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

private enum class DateTimePickerStep { DATE, TIME }

// IsoDatePickerDialog와 달리 시각(시/분)까지 다루므로, 사용자가 폰에서 보는 시각과 일치하도록
// 시스템 기본 타임존을 사용한다(IsoDatePickerDialog는 날짜만 다뤄 UTC로 통일하는 것과 다른 트레이드오프).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IsoDateTimePickerDialog(
    initialInstant: Instant?,
    onDismiss: () -> Unit,
    onConfirm: (Instant) -> Unit,
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val initialZoned = remember(initialInstant) { (initialInstant ?: Instant.now()).atZone(zoneId) }

    var step by rememberSaveable { mutableStateOf(DateTimePickerStep.DATE) }
    var pickedDate by remember { mutableStateOf(initialZoned.toLocalDate()) }

    // DatePickerState.selectedDateMillis는 UTC 자정 기준 epoch millis로 해석되므로(IsoDatePickerDialog와 동일한
    // 제약), 초기값도 로컬 날짜의 UTC 자정으로 맞춰야 한다. 그대로 initialZoned의 instant를 넘기면 로컬
    // 타임존이 UTC와 다를 때(KST 새벽 시간대 등) 하루 다른 날짜가 선택된 상태로 표시된다.
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialZoned.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    val timePickerState = rememberTimePickerState(
        initialHour = initialZoned.hour,
        initialMinute = initialZoned.minute,
    )

    when (step) {
        DateTimePickerStep.DATE -> DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis == null) {
                        onDismiss()
                        return@TextButton
                    }
                    pickedDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    step = DateTimePickerStep.TIME
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
        DateTimePickerStep.TIME -> AlertDialog(
            onDismissRequest = onDismiss,
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val combined = LocalDate.of(pickedDate.year, pickedDate.month, pickedDate.dayOfMonth)
                        .atTime(timePickerState.hour, timePickerState.minute)
                        .atZone(zoneId)
                        .toInstant()
                    onConfirm(combined)
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
            }
        )
    }
}
