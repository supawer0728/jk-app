package com.jkapp.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jkapp.R
import com.jkapp.common.IsoDateTimePickerDialog
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DUE_AT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private val REMINDER_PRESETS: List<Int?> = listOf(null, 0, 10, 30, 60, 1440)

// TodoFormScreen은 카테고리 드롭다운의 "카테고리 관리" 진입점에서 전역 backStack에 별도 라우트를
// push한다(NavRoutes.TodoCategoryManagementRoute). Navigation3는 최상단이 아닌 엔트리를 컴포지션에서
// 제거하므로 plain remember 상태는 그 사이 사라진다 — 이 값들은 Instant/enum/List/데이터클래스라
// autoSaver가 다루지 못해 원래 plain remember로 남겨뒀던 것들이라, 여기서는 커스텀 Saver로
// rememberSaveable을 써서 카테고리 관리 화면을 거쳐 돌아와도 입력값이 보존되도록 한다.
private val InstantOrNullSaver = Saver<Instant?, String>(
    save = { it?.toString() ?: "" },
    restore = { if (it.isEmpty()) null else Instant.parse(it) }
)

private val TodoPrioritySaver = Saver<TodoPriority, String>(
    save = { it.name },
    restore = { TodoPriority.valueOf(it) }
)

private val StringListSaver = Saver<List<String>, ArrayList<String>>(
    save = { ArrayList(it) },
    restore = { it.toList() }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoFormScreen(
    viewModel: TodoViewModel,
    firestoreId: String?,
    onBack: () -> Unit,
    onNavigateToCategoryManagement: () -> Unit,
) {
    val isEditMode = firestoreId != null
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val success = uiState as? TodoUiState.Success
    val categories = success?.categories ?: emptyList()
    val existingItem = if (isEditMode) success?.items?.find { it.firestoreId == firestoreId } else null

    var title by rememberSaveable { mutableStateOf(existingItem?.title ?: "") }
    var memo by rememberSaveable { mutableStateOf(existingItem?.memo ?: "") }
    var dueAt by rememberSaveable(stateSaver = InstantOrNullSaver) { mutableStateOf(existingItem?.dueAt) }
    var priority by rememberSaveable(stateSaver = TodoPrioritySaver) { mutableStateOf(existingItem?.priority ?: TodoPriority.NONE) }
    var categoryId by rememberSaveable { mutableStateOf(existingItem?.categoryId) }
    var tags by rememberSaveable(stateSaver = StringListSaver) { mutableStateOf(existingItem?.tags ?: emptyList()) }
    var recurrence by rememberSaveable(stateSaver = RecurrenceRuleOrNullSaver) { mutableStateOf(existingItem?.recurrence) }
    var reminderOffsetMinutes by rememberSaveable { mutableStateOf(existingItem?.reminderOffsetMinutes) }

    var priorityDropdownExpanded by remember { mutableStateOf(false) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var reminderDropdownExpanded by remember { mutableStateOf(false) }
    var showDueAtPicker by rememberSaveable { mutableStateOf(false) }
    var showRecurrenceDialog by rememberSaveable { mutableStateOf(false) }
    var tagInput by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(existingItem) {
        existingItem?.let { item ->
            title = item.title
            memo = item.memo
            dueAt = item.dueAt
            priority = item.priority
            categoryId = item.categoryId
            tags = item.tags
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
    val selectedCategory = categories.find { it.docId == categoryId }

    if (showDueAtPicker) {
        IsoDateTimePickerDialog(
            initialInstant = dueAt,
            onDismiss = { showDueAtPicker = false },
            onConfirm = { instant ->
                dueAt = instant
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

            OutlinedTextField(
                value = memo,
                onValueChange = { memo = it },
                label = { Text(stringResource(R.string.todo_field_memo)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            Box {
                OutlinedTextField(
                    value = dueAt?.let {
                        DUE_AT_FORMATTER.format(it.atZone(ZoneId.systemDefault()))
                    } ?: stringResource(R.string.todo_due_at_none),
                    onValueChange = {},
                    label = { Text(stringResource(R.string.todo_field_due_at)) },
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        Row {
                            if (dueAt != null) {
                                IconButton(onClick = { dueAt = null }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.todo_due_at_clear))
                                }
                            }
                            Icon(Icons.Default.DateRange, contentDescription = null)
                        }
                    }
                )
                Box(modifier = Modifier.matchParentSize().clickable { showDueAtPicker = true })
            }

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

            ExposedDropdownMenuBox(
                expanded = categoryDropdownExpanded,
                onExpandedChange = { categoryDropdownExpanded = !categoryDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = selectedCategory?.let { "${it.emoji} ${it.name}" }
                        ?: stringResource(R.string.todo_category_none),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.todo_field_category)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = categoryDropdownExpanded,
                    onDismissRequest = { categoryDropdownExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.todo_category_none)) },
                        onClick = {
                            categoryId = null
                            categoryDropdownExpanded = false
                        }
                    )
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text("${category.emoji} ${category.name}") },
                            onClick = {
                                categoryId = category.docId
                                categoryDropdownExpanded = false
                            }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.todo_category_manage_entry)) },
                        onClick = {
                            categoryDropdownExpanded = false
                            onNavigateToCategoryManagement()
                        }
                    )
                }
            }

            TagInputSection(
                tags = tags,
                tagInput = tagInput,
                onTagInputChange = { tagInput = it },
                onAddTag = {
                    val trimmed = tagInput.trim()
                    if (trimmed.isNotEmpty() && trimmed !in tags) {
                        tags = tags + trimmed
                    }
                    tagInput = ""
                },
                onRemoveTag = { tag -> tags = tags.filter { it != tag } },
            )

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
                            }
                        )
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
                        isCompleted = existingItem?.isCompleted ?: false,
                        dueAt = dueAt,
                        reminderOffsetMinutes = if (dueAt != null) reminderOffsetMinutes else null,
                        priority = priority,
                        // categoryId를 그대로 쓰지 않고 selectedCategory?.docId로 다시 확인한다 — 카테고리 관리
                        // 화면에서 현재 선택된 카테고리가 삭제된 뒤 돌아온 경우, 드롭다운은 "카테고리 없음"으로
                        // 보여주지만 categoryId 변수 자체는 삭제된 카테고리 ID를 여전히 들고 있어 그대로 저장하면
                        // 더 이상 존재하지 않는 카테고리를 참조하게 된다.
                        categoryId = selectedCategory?.docId,
                        tags = tags,
                        recurrence = recurrence,
                        completionHistory = existingItem?.completionHistory ?: emptyList(),
                        createdAt = existingItem?.createdAt,
                        completedAt = existingItem?.completedAt,
                    )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagInputSection(
    tags: List<String>,
    tagInput: String,
    onTagInputChange: (String) -> Unit,
    onAddTag: () -> Unit,
    onRemoveTag: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = tagInput,
                onValueChange = onTagInputChange,
                label = { Text(stringResource(R.string.todo_field_tag_input)) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onAddTag) {
                Text(stringResource(R.string.todo_tag_add))
            }
        }
        if (tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = { Text(tag) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.todo_tag_delete),
                                modifier = Modifier
                                    .height(16.dp)
                                    .clickable { onRemoveTag(tag) }
                            )
                        }
                    )
                }
            }
        }
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
