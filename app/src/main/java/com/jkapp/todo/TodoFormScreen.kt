package com.jkapp.todo

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.jkapp.R
import com.jkapp.common.IsoDateTimePickerDialog
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val DUE_AT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private val REMINDER_PRESETS: List<Int?> = listOf(null, 0, 10, 30, 60, 1440)

// 직접 입력한 "yyyy-MM-dd HH:mm" 문자열을 Instant로 파싱한다. 형식이 맞지 않으면 null.
private fun parseDueAt(text: String, zone: ZoneId): Instant? =
    runCatching { LocalDateTime.parse(text.trim(), DUE_AT_FORMATTER).atZone(zone).toInstant() }.getOrNull()

private fun formatDueAt(instant: Instant?, zone: ZoneId): String =
    instant?.let { DUE_AT_FORMATTER.format(it.atZone(zone)) } ?: ""

// TodoFormScreen의 입력 상태 중 Instant/enum/데이터클래스 값은 autoSaver가 다루지 못하므로 커스텀
// Saver로 rememberSaveable을 써서 프로세스 종료/복원 시에도 입력값이 보존되도록 한다.
private val InstantOrNullSaver = Saver<Instant?, String>(
    save = { it?.toString() ?: "" },
    restore = { if (it.isEmpty()) null else Instant.parse(it) }
)

private val TodoPrioritySaver = Saver<TodoPriority, String>(
    save = { it.name },
    restore = { TodoPriority.valueOf(it) }
)

private val TodoAssigneeSaver = Saver<TodoAssignee, String>(
    save = { it.name },
    restore = { TodoAssignee.fromNameOrDefault(it) }
)

private val RecurrenceRuleOrNullSaver = Saver<RecurrenceRule?, List<Any?>>(
    save = { rule ->
        if (rule == null) {
            listOf(null)
        } else {
            listOf(rule.frequency.name, rule.interval, rule.daysOfWeek.toList(), rule.endAt?.toString(), rule.anchorDay)
        }
    },
    restore = { saved ->
        val frequencyName = saved.getOrNull(0) as? String ?: return@Saver null
        RecurrenceRule(
            frequency = RecurrenceFrequency.valueOf(frequencyName),
            interval = (saved.getOrNull(1) as? Int) ?: 1,
            daysOfWeek = (saved.getOrNull(2) as? List<*>)?.mapNotNull { it as? Int }?.toSet() ?: emptySet(),
            endAt = (saved.getOrNull(3) as? String)?.let { Instant.parse(it) },
            anchorDay = saved.getOrNull(4) as? Int,
        )
    }
)

@Composable
private fun reminderPresetLabel(minutes: Int?): String = when (minutes) {
    null -> stringResource(R.string.todo_reminder_none)
    0 -> stringResource(R.string.todo_reminder_on_time)
    10 -> stringResource(R.string.todo_reminder_10min)
    30 -> stringResource(R.string.todo_reminder_30min)
    60 -> stringResource(R.string.todo_reminder_1hour)
    1440 -> stringResource(R.string.todo_reminder_1day)
    else -> minutes.toString()
}

@Composable
private fun priorityLabel(priority: TodoPriority): String = when (priority) {
    TodoPriority.NONE -> stringResource(R.string.todo_priority_none)
    TodoPriority.LOW -> stringResource(R.string.todo_priority_low)
    TodoPriority.MEDIUM -> stringResource(R.string.todo_priority_medium)
    TodoPriority.HIGH -> stringResource(R.string.todo_priority_high)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun TodoFormScreen(
    viewModel: TodoViewModel,
    firestoreId: String?,
    onBack: () -> Unit,
) {
    val isEditMode = firestoreId != null
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val success = uiState as? TodoUiState.Success
    val existingItem = if (isEditMode) success?.items?.find { it.firestoreId == firestoreId } else null

    val zone = remember { ZoneId.systemDefault() }

    var title by rememberSaveable { mutableStateOf(existingItem?.title ?: "") }
    var assignee by rememberSaveable(stateSaver = TodoAssigneeSaver) { mutableStateOf(existingItem?.assignee ?: TodoAssignee.DEFAULT) }
    var memo by rememberSaveable { mutableStateOf(existingItem?.memo ?: "") }
    var dueAt by rememberSaveable(stateSaver = InstantOrNullSaver) { mutableStateOf(existingItem?.dueAt) }
    var dueAtText by rememberSaveable { mutableStateOf(formatDueAt(existingItem?.dueAt, zone)) }
    var priority by rememberSaveable(stateSaver = TodoPrioritySaver) { mutableStateOf(existingItem?.priority ?: TodoPriority.NONE) }
    var recurrence by rememberSaveable(stateSaver = RecurrenceRuleOrNullSaver) { mutableStateOf(existingItem?.recurrence) }
    var reminderOffsetMinutes by rememberSaveable { mutableStateOf(existingItem?.reminderOffsetMinutes) }
    // 기본 노출은 제목+담당자만. 수정 모드에서는 기존 상세값을 바로 볼 수 있게 펼친 상태로 시작한다.
    var detailsExpanded by rememberSaveable { mutableStateOf(isEditMode) }

    var priorityDropdownExpanded by remember { mutableStateOf(false) }
    var reminderDropdownExpanded by remember { mutableStateOf(false) }
    var showDueAtPicker by rememberSaveable { mutableStateOf(false) }
    var showRecurrenceDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(existingItem) {
        existingItem?.let { item ->
            title = item.title
            assignee = item.assignee
            memo = item.memo
            dueAt = item.dueAt
            dueAtText = formatDueAt(item.dueAt, zone)
            priority = item.priority
            recurrence = item.recurrence
            reminderOffsetMinutes = item.reminderOffsetMinutes
        }
    }

    val saveCompleted by viewModel.saveCompleted.collectAsStateWithLifecycle()
    LaunchedEffect(saveCompleted) {
        if (saveCompleted) {
            viewModel.consumeSaveCompleted()
            onBack()
        }
    }

    val isDataReady = !isEditMode || existingItem != null
    val isValid = isDataReady && title.isNotBlank()

    // 리마인더를 켤 때(없음이 아닌 프리셋 선택 시) POST_NOTIFICATIONS 권한을 요청한다. 거부해도 저장은
    // 그대로 진행하고, 알림이 표시되지 않는다는 점만 스낵바로 경고한다.
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val reminderPermissionDeniedMessage = stringResource(R.string.todo_reminder_permission_denied)
    val notificationPermissionState = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS) { granted ->
        if (!granted) {
            scope.launch { snackbarHostState.showSnackbar(reminderPermissionDeniedMessage) }
        }
    }

    if (showDueAtPicker) {
        IsoDateTimePickerDialog(
            initialInstant = dueAt,
            onDismiss = { showDueAtPicker = false },
            onConfirm = { instant ->
                dueAt = instant
                dueAtText = formatDueAt(instant, zone)
                showDueAtPicker = false
            },
        )
    }

    if (showRecurrenceDialog) {
        RecurrenceSettingDialog(
            initialRecurrence = recurrence,
            onDismiss = { showRecurrenceDialog = false },
            onConfirm = { newRecurrence ->
                recurrence = newRecurrence
                showRecurrenceDialog = false
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (isEditMode) R.string.todo_form_edit_title else R.string.todo_form_add_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.todo_field_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            AssigneeSelector(selected = assignee, onSelect = { assignee = it })

            OutlinedButton(
                onClick = { detailsExpanded = !detailsExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(
                        if (detailsExpanded) R.string.todo_form_collapse_details
                        else R.string.todo_form_expand_details
                    ),
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (detailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                )
            }

            if (detailsExpanded) {
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text(stringResource(R.string.todo_field_memo)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                // 마감일시: 직접 타이핑(yyyy-MM-dd HH:mm) + 달력 아이콘으로 피커. 파싱되면 dueAt을 갱신하고,
                // 형식이 안 맞으면 dueAt은 null로 두되 입력 텍스트는 유지한다.
                OutlinedTextField(
                    value = dueAtText,
                    onValueChange = {
                        dueAtText = it
                        dueAt = parseDueAt(it, zone)
                    },
                    label = { Text(stringResource(R.string.todo_field_due_at)) },
                    placeholder = { Text(stringResource(R.string.todo_due_at_hint)) },
                    singleLine = true,
                    isError = dueAtText.isNotBlank() && dueAt == null,
                    // yyyy-MM-dd HH:mm은 '-' ':' 공백 구분자가 필요해 숫자 전용 키패드를 쓰지 않는다.
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        Row {
                            if (dueAtText.isNotEmpty()) {
                                IconButton(onClick = {
                                    dueAt = null
                                    dueAtText = ""
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.todo_due_at_clear))
                                }
                            }
                            IconButton(onClick = { showDueAtPicker = true }) {
                                Icon(Icons.Default.DateRange, contentDescription = stringResource(R.string.todo_due_at_pick))
                            }
                        }
                    }
                )

                ExposedDropdownMenuBox(
                    expanded = priorityDropdownExpanded,
                    onExpandedChange = { priorityDropdownExpanded = !priorityDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = priorityLabel(priority),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.todo_field_priority)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = priorityDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = priorityDropdownExpanded,
                        onDismissRequest = { priorityDropdownExpanded = false }
                    ) {
                        TodoPriority.entries.forEach { entry ->
                            DropdownMenuItem(
                                text = { Text(priorityLabel(entry)) },
                                onClick = {
                                    priority = entry
                                    priorityDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { showRecurrenceDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("${stringResource(R.string.todo_field_recurrence)}: ${recurrenceSummary(recurrence)}")
                }

                ExposedDropdownMenuBox(
                    expanded = reminderDropdownExpanded,
                    onExpandedChange = { reminderDropdownExpanded = !reminderDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = reminderPresetLabel(reminderOffsetMinutes),
                        onValueChange = {},
                        readOnly = true,
                        enabled = dueAt != null,
                        label = { Text(stringResource(R.string.todo_field_reminder)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = reminderDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = reminderDropdownExpanded,
                        onDismissRequest = { reminderDropdownExpanded = false }
                    ) {
                        REMINDER_PRESETS.forEach { minutes ->
                            DropdownMenuItem(
                                text = { Text(reminderPresetLabel(minutes)) },
                                onClick = {
                                    reminderOffsetMinutes = minutes
                                    reminderDropdownExpanded = false
                                    // 리마인더를 처음 켤 때(없음이 아닌 프리셋)만 알림 권한을 요청한다.
                                    if (minutes != null && !notificationPermissionState.status.isGranted) {
                                        notificationPermissionState.launchPermissionRequest()
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    val item = TodoItem(
                        firestoreId = firestoreId,
                        title = title.trim(),
                        memo = memo.trim(),
                        status = existingItem?.status ?: TodoStatus.NOT_STARTED,
                        assignee = assignee,
                        dueAt = dueAt,
                        reminderOffsetMinutes = if (dueAt != null) reminderOffsetMinutes else null,
                        priority = priority,
                        recurrence = recurrence,
                        completionHistory = existingItem?.completionHistory ?: emptyList(),
                        createdAt = existingItem?.createdAt,
                        completedAt = existingItem?.completedAt,
                    )
                    // 리마인더가 예약될 항목인데 알림 권한이 없으면 저장 시점에도 권한을 요청한다. 리마인더가
                    // 이미 설정된 항목을 드롭다운을 다시 건드리지 않고 저장하는 편집 흐름에서 알림이 조용히
                    // 누락되는 것을 막는다(권한 부여는 저장 후에도 유효하므로 예약된 Worker가 알림을 띄운다).
                    if (TodoViewModel.shouldScheduleReminder(item) && !notificationPermissionState.status.isGranted) {
                        notificationPermissionState.launchPermissionRequest()
                    }
                    if (isEditMode) {
                        viewModel.updateTodoItem(item)
                    } else {
                        viewModel.addTodoItem(item)
                    }
                },
                enabled = isValid,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssigneeSelector(
    selected: TodoAssignee,
    onSelect: (TodoAssignee) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.todo_field_assignee), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TodoAssignee.entries.forEach { entry ->
                FilterChip(
                    selected = selected == entry,
                    onClick = { onSelect(entry) },
                    label = { Text(assigneeLabel(entry)) }
                )
            }
        }
    }
}

@Composable
private fun recurrenceSummary(recurrence: RecurrenceRule?): String {
    if (recurrence == null) return stringResource(R.string.todo_recurrence_none)
    return when (recurrence.frequency) {
        RecurrenceFrequency.DAILY -> stringResource(R.string.todo_recurrence_daily)
        RecurrenceFrequency.WEEKLY -> stringResource(R.string.todo_recurrence_weekly)
        RecurrenceFrequency.MONTHLY -> stringResource(R.string.todo_recurrence_monthly)
        RecurrenceFrequency.YEARLY -> stringResource(R.string.todo_recurrence_yearly)
    }
}

@Composable
private fun RecurrenceSettingDialog(
    initialRecurrence: RecurrenceRule?,
    onDismiss: () -> Unit,
    onConfirm: (RecurrenceRule?) -> Unit,
) {
    var frequency by remember { mutableStateOf(initialRecurrence?.frequency) }
    var interval by remember { mutableStateOf((initialRecurrence?.interval ?: 1).toString()) }
    var daysOfWeek by remember { mutableStateOf(initialRecurrence?.daysOfWeek ?: emptySet()) }
    var endDatePicked by remember { mutableStateOf(initialRecurrence?.endAt) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    if (showEndDatePicker) {
        IsoDateTimePickerDialog(
            initialInstant = endDatePicked,
            onDismiss = { showEndDatePicker = false },
            onConfirm = { instant ->
                endDatePicked = instant
                showEndDatePicker = false
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.todo_field_recurrence)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRowFrequencyOptions(
                    selected = frequency,
                    onSelect = { frequency = it }
                )
                if (frequency != null) {
                    OutlinedTextField(
                        value = interval,
                        onValueChange = { value -> if (value.all { it.isDigit() }) interval = value },
                        label = { Text(stringResource(R.string.todo_recurrence_interval)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (frequency == RecurrenceFrequency.WEEKLY) {
                    WeekdaySelector(
                        selected = daysOfWeek,
                        onToggle = { day ->
                            daysOfWeek = if (day in daysOfWeek) daysOfWeek - day else daysOfWeek + day
                        }
                    )
                }
                if (frequency != null) {
                    OutlinedButton(
                        onClick = { showEndDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val label = endDatePicked?.let {
                            DUE_AT_FORMATTER.format(it.atZone(ZoneId.systemDefault()))
                        } ?: stringResource(R.string.todo_recurrence_end_date_none)
                        Text("${stringResource(R.string.todo_recurrence_end_date)}: $label")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val resolvedFrequency = frequency
                if (resolvedFrequency == null) {
                    onConfirm(null)
                } else {
                    onConfirm(
                        RecurrenceRule(
                            frequency = resolvedFrequency,
                            interval = interval.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                            daysOfWeek = daysOfWeek,
                            endAt = endDatePicked,
                            anchorDay = initialRecurrence?.takeIf { it.frequency == resolvedFrequency }?.anchorDay,
                        )
                    )
                }
            }) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowFrequencyOptions(
    selected: RecurrenceFrequency?,
    onSelect: (RecurrenceFrequency?) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.todo_recurrence_none)) }
        )
        FilterChip(
            selected = selected == RecurrenceFrequency.DAILY,
            onClick = { onSelect(RecurrenceFrequency.DAILY) },
            label = { Text(stringResource(R.string.todo_recurrence_daily)) }
        )
        FilterChip(
            selected = selected == RecurrenceFrequency.WEEKLY,
            onClick = { onSelect(RecurrenceFrequency.WEEKLY) },
            label = { Text(stringResource(R.string.todo_recurrence_weekly)) }
        )
        FilterChip(
            selected = selected == RecurrenceFrequency.MONTHLY,
            onClick = { onSelect(RecurrenceFrequency.MONTHLY) },
            label = { Text(stringResource(R.string.todo_recurrence_monthly)) }
        )
        FilterChip(
            selected = selected == RecurrenceFrequency.YEARLY,
            onClick = { onSelect(RecurrenceFrequency.YEARLY) },
            label = { Text(stringResource(R.string.todo_recurrence_yearly)) }
        )
    }
}

private val WEEKDAY_ORDER = listOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
)

@Composable
private fun weekdayLabel(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> stringResource(R.string.todo_weekday_mon)
    DayOfWeek.TUESDAY -> stringResource(R.string.todo_weekday_tue)
    DayOfWeek.WEDNESDAY -> stringResource(R.string.todo_weekday_wed)
    DayOfWeek.THURSDAY -> stringResource(R.string.todo_weekday_thu)
    DayOfWeek.FRIDAY -> stringResource(R.string.todo_weekday_fri)
    DayOfWeek.SATURDAY -> stringResource(R.string.todo_weekday_sat)
    DayOfWeek.SUNDAY -> stringResource(R.string.todo_weekday_sun)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekdaySelector(
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        WEEKDAY_ORDER.forEach { day ->
            FilterChip(
                selected = day.value in selected,
                onClick = { onToggle(day.value) },
                label = { Text(weekdayLabel(day)) }
            )
        }
    }
}
