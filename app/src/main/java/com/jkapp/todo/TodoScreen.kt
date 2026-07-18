package com.jkapp.todo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jkapp.R
import com.jkapp.common.LoadingIndicator
import com.jkapp.common.MultiDeleteBar
import com.jkapp.common.MultiDeleteState
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DUE_AT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

@Composable
private fun statusFilterLabel(filter: TodoStatusFilter): String = when (filter) {
    TodoStatusFilter.TODAY -> stringResource(R.string.todo_filter_today)
    TodoStatusFilter.ALL -> stringResource(R.string.todo_filter_all)
    TodoStatusFilter.RECURRING -> stringResource(R.string.todo_filter_recurring)
    TodoStatusFilter.COMPLETED -> stringResource(R.string.todo_filter_completed)
}

@Composable
fun assigneeLabel(assignee: TodoAssignee): String = when (assignee) {
    TodoAssignee.SHARED -> stringResource(R.string.todo_assignee_shared)
    TodoAssignee.KWON_YUKYEONG -> stringResource(R.string.todo_assignee_kwon)
    TodoAssignee.JEON_JIHOON -> stringResource(R.string.todo_assignee_jeon)
}

@Composable
private fun statusLabel(status: TodoStatus): String = when (status) {
    TodoStatus.NOT_STARTED -> stringResource(R.string.todo_status_not_started)
    TodoStatus.IN_PROGRESS -> stringResource(R.string.todo_status_in_progress)
    TodoStatus.DONE -> stringResource(R.string.todo_status_done)
}

@Composable
private fun priorityLabel(priority: TodoPriority): String = when (priority) {
    TodoPriority.NONE -> stringResource(R.string.todo_priority_none)
    TodoPriority.LOW -> stringResource(R.string.todo_priority_low)
    TodoPriority.MEDIUM -> stringResource(R.string.todo_priority_medium)
    TodoPriority.HIGH -> stringResource(R.string.todo_priority_high)
}

@Composable
fun TodoScreen(
    viewModel: TodoViewModel,
    onNavigateToForm: (String?) -> Unit,
    onNavigateToSubForm: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val visibleItems by viewModel.visibleItems.collectAsStateWithLifecycle()
    val statusFilter by viewModel.statusFilter.collectAsStateWithLifecycle()
    val assigneeFilter by viewModel.assigneeFilter.collectAsStateWithLifecycle()
    val pendingSubClone by viewModel.pendingSubClone.collectAsStateWithLifecycle()

    val multiDeleteState = remember { MultiDeleteState<String>() }
    // 펼쳐진 부모 ID 집합. 접힘이 기본값.
    var expandedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is TodoUiState.Loading -> {
                LoadingIndicator(modifier = Modifier.align(Alignment.Center))
            }
            is TodoUiState.Error -> {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                )
            }
            is TodoUiState.Success -> {
                // 부모별 자식 목록(펼침 영역에서만 노출). state.items에서 직접 필터링해 실시간 반영.
                val subsByParent = remember(state.items) {
                    state.items
                        .filter { it.type == TodoType.SUB && it.mainTodoId != null }
                        .groupBy { it.mainTodoId!! }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    StatusFilterRow(selected = statusFilter, onSelect = viewModel::setStatusFilter)
                    AssigneeFilterRow(
                        selected = assigneeFilter,
                        onToggle = viewModel::toggleAssigneeFilter,
                        onClearFilter = viewModel::clearAssigneeFilter,
                    )

                    if (visibleItems.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.todo_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(visibleItems, key = { it.firestoreId ?: it.hashCode() }) { item ->
                                val parentId = item.firestoreId
                                val subs = parentId?.let { subsByParent[it].orEmpty() }.orEmpty()
                                TodoListItem(
                                    item = item,
                                    subItems = subs,
                                    isExpanded = parentId != null && parentId in expandedIds,
                                    onToggleExpanded = {
                                        if (parentId != null) {
                                            expandedIds = if (parentId in expandedIds) {
                                                expandedIds - parentId
                                            } else {
                                                expandedIds + parentId
                                            }
                                        }
                                    },
                                    isSelectionMode = multiDeleteState.isSelectionMode,
                                    isSelected = parentId != null &&
                                        parentId in multiDeleteState.selectedIds,
                                    onToggleSelected = {
                                        if (parentId != null) multiDeleteState.toggle(parentId)
                                    },
                                    onAdvanceStatus = { viewModel.advanceStatus(item) },
                                    onClick = { onNavigateToForm(item.firestoreId) },
                                    onAddSub = { parentId?.let(onNavigateToSubForm) },
                                    onAdvanceSubStatus = { sub -> viewModel.advanceSubStatus(sub) },
                                )
                            }
                        }
                    }
                }

                if (multiDeleteState.isSelectionMode) {
                    MultiDeleteBar(
                        selectedCount = multiDeleteState.selectedIds.size,
                        onDeleteSelected = {
                            val ids = multiDeleteState.selectedIds
                            val selected = visibleItems.filter { it.firestoreId in ids }
                            viewModel.deleteTodoItems(selected)
                            multiDeleteState.exit()
                        },
                        onCancel = { multiDeleteState.exit() },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (visibleItems.isNotEmpty()) {
                            FloatingActionButton(onClick = { multiDeleteState.enter() }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.todo_bulk_delete),
                                )
                            }
                        }
                        FloatingActionButton(onClick = { onNavigateToForm(null) }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.todo_add),
                            )
                        }
                    }
                }
            }
        }
    }

    // 반복 부모 완료 시 자식 복제 확인 다이얼로그(목록 사이클 완료 경로에서만).
    pendingSubClone?.let { (parentId, subs) ->
        AlertDialog(
            onDismissRequest = { viewModel.consumePendingSubClone() },
            title = { Text(stringResource(R.string.todo_sub_clone_title)) },
            text = { Text(stringResource(R.string.todo_sub_clone_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.cloneSubsToNextOccurrence(parentId, subs)
                    viewModel.consumePendingSubClone()
                }) {
                    Text(stringResource(R.string.todo_sub_clone_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.consumePendingSubClone() }) {
                    Text(stringResource(R.string.todo_sub_clone_dismiss))
                }
            }
        )
    }
}

@Composable
private fun StatusFilterRow(
    selected: TodoStatusFilter,
    onSelect: (TodoStatusFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TodoStatusFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                label = { Text(statusFilterLabel(filter)) }
            )
        }
    }
}

@Composable
private fun AssigneeFilterRow(
    selected: Set<TodoAssignee>,
    onToggle: (TodoAssignee) -> Unit,
    onClearFilter: () -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
    ) {
        item {
            FilterChip(
                selected = selected.isEmpty(),
                onClick = onClearFilter,
                label = { Text(stringResource(R.string.filter_all)) }
            )
        }
        items(TodoAssignee.entries) { assignee ->
            FilterChip(
                selected = assignee in selected,
                onClick = { onToggle(assignee) },
                label = { Text(assigneeLabel(assignee)) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodoListItem(
    item: TodoItem,
    subItems: List<TodoItem>,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelected: () -> Unit,
    onAdvanceStatus: () -> Unit,
    onClick: () -> Unit,
    onAddSub: () -> Unit,
    onAdvanceSubStatus: (TodoItem) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = if (isSelectionMode) onToggleSelected else onClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectionMode) {
                    Checkbox(checked = isSelected, onCheckedChange = { onToggleSelected() })
                } else {
                    // 상태 사이클 버튼: 미진행 -(재생)-> 진행중 -(재생)-> 완료 -(처음으로)-> 미진행.
                    IconButton(onClick = onAdvanceStatus) {
                        Icon(
                            imageVector = if (item.status == TodoStatus.DONE) Icons.Default.SkipPrevious else Icons.Default.PlayArrow,
                            contentDescription = stringResource(
                                when (item.status) {
                                    TodoStatus.NOT_STARTED -> R.string.todo_status_toggle_start
                                    TodoStatus.IN_PROGRESS -> R.string.todo_status_advance_complete
                                    TodoStatus.DONE -> R.string.todo_status_advance_reset
                                }
                            ),
                            tint = when (item.status) {
                                TodoStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
                                TodoStatus.DONE -> MaterialTheme.colorScheme.onSurfaceVariant
                                TodoStatus.NOT_STARTED -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        if (item.status == TodoStatus.IN_PROGRESS) {
                            SuggestionChip(
                                onClick = {},
                                label = { Text(statusLabel(item.status), style = MaterialTheme.typography.labelSmall) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                border = null,
                            )
                        }
                        SuggestionChip(
                            onClick = {},
                            label = { Text(assigneeLabel(item.assignee), style = MaterialTheme.typography.labelSmall) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            ),
                            border = null,
                        )
                        item.dueAt?.let { dueAt ->
                            SuggestionChip(
                                onClick = {},
                                label = { Text(DUE_AT_FORMATTER.format(dueAt.atZone(ZoneId.systemDefault())), style = MaterialTheme.typography.labelSmall) },
                                border = null,
                            )
                        }
                        if (item.priority != TodoPriority.NONE) {
                            SuggestionChip(
                                onClick = {},
                                label = { Text(priorityLabel(item.priority), style = MaterialTheme.typography.labelSmall) },
                                border = null,
                            )
                        }
                    }
                }
                // 선택 모드가 아닐 때만 자식 추가·펼치기 버튼 노출.
                if (!isSelectionMode) {
                    IconButton(onClick = onAddSub) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.todo_add_sub),
                        )
                    }
                    if (subItems.isNotEmpty()) {
                        IconButton(onClick = onToggleExpanded) {
                            Icon(
                                imageVector = if (isExpanded) {
                                    Icons.Default.ExpandLess
                                } else {
                                    Icons.Default.ExpandMore
                                },
                                contentDescription = stringResource(
                                    if (isExpanded) {
                                        R.string.todo_collapse_subs
                                    } else {
                                        R.string.todo_expand_subs
                                    }
                                ),
                            )
                        }
                    }
                }
            }

            // 펼침 영역: 자식 목록. 선택 모드에서는 자식을 노출하지 않는다(부모 단위 삭제).
            if (isExpanded && !isSelectionMode && subItems.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 32.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    subItems.forEach { sub ->
                        SubTodoListItem(sub = sub, onAdvanceStatus = { onAdvanceSubStatus(sub) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SubTodoListItem(
    sub: TodoItem,
    onAdvanceStatus: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onAdvanceStatus) {
            Icon(
                imageVector = if (sub.status == TodoStatus.DONE) {
                    Icons.Default.SkipPrevious
                } else {
                    Icons.Default.PlayArrow
                },
                contentDescription = stringResource(
                    when (sub.status) {
                        TodoStatus.NOT_STARTED -> R.string.todo_status_toggle_start
                        TodoStatus.IN_PROGRESS -> R.string.todo_status_advance_complete
                        TodoStatus.DONE -> R.string.todo_status_advance_reset
                    }
                ),
                tint = when (sub.status) {
                    TodoStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Text(
            text = sub.title,
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = if (sub.isCompleted) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
