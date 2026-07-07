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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DUE_AT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

@Composable
private fun statusFilterLabel(filter: TodoStatusFilter): String = when (filter) {
    TodoStatusFilter.ALL -> stringResource(R.string.todo_status_all)
    TodoStatusFilter.ACTIVE -> stringResource(R.string.todo_status_active)
    TodoStatusFilter.COMPLETED -> stringResource(R.string.todo_status_completed)
}

@Composable
private fun sortOptionLabel(option: TodoSortOption): String = when (option) {
    TodoSortOption.DUE_DATE -> stringResource(R.string.todo_sort_due_date)
    TodoSortOption.PRIORITY -> stringResource(R.string.todo_sort_priority)
    TodoSortOption.CREATED_AT -> stringResource(R.string.todo_sort_created_at)
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
    onNavigateToCategoryManagement: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val visibleItems by viewModel.visibleItems.collectAsStateWithLifecycle()
    val statusFilter by viewModel.statusFilter.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.categoryFilter.collectAsStateWithLifecycle()
    val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()

    var pendingDeleteItem by remember { mutableStateOf<TodoItem?>(null) }

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
                val allTags = remember(state.items) { state.items.flatMap { it.tags }.distinct().sorted() }

                Column(modifier = Modifier.fillMaxSize()) {
                    StatusFilterRow(selected = statusFilter, onSelect = viewModel::setStatusFilter)
                    CategoryFilterRow(
                        categories = state.categories,
                        selectedIds = categoryFilter,
                        onToggle = viewModel::toggleCategoryFilter,
                        onClearFilter = viewModel::clearCategoryFilter,
                    )
                    if (allTags.isNotEmpty()) {
                        TagFilterRow(
                            tags = allTags,
                            selectedTags = tagFilter,
                            onToggle = viewModel::toggleTagFilter,
                            onClearFilter = viewModel::clearTagFilter,
                        )
                    }
                    SortRow(selected = sortOption, onSelect = viewModel::setSortOption)

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
                                TodoListItem(
                                    item = item,
                                    category = state.categories.find { it.docId == item.categoryId },
                                    onToggleCompleted = { viewModel.toggleCompleted(item) },
                                    onClick = { onNavigateToForm(item.firestoreId) },
                                    onDeleteRequest = { pendingDeleteItem = item },
                                )
                            }
                        }
                    }
                }

                FloatingActionButton(
                    onClick = { onNavigateToForm(null) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.todo_add))
                }
            }
        }
    }

    pendingDeleteItem?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDeleteItem = null },
            title = { Text(stringResource(R.string.todo_delete_confirm_title)) },
            text = { Text(stringResource(R.string.todo_delete_confirm_message, item.title)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTodoItem(item)
                    pendingDeleteItem = null
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteItem = null }) {
                    Text(stringResource(R.string.cancel))
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
private fun CategoryFilterRow(
    categories: List<TodoCategory>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onClearFilter: () -> Unit,
) {
    if (categories.isEmpty()) return
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
    ) {
        item {
            FilterChip(
                selected = selectedIds.isEmpty(),
                onClick = onClearFilter,
                label = { Text(stringResource(R.string.filter_all)) }
            )
        }
        items(categories, key = { it.docId }) { category ->
            FilterChip(
                selected = category.docId in selectedIds,
                onClick = { onToggle(category.docId) },
                label = { Text("${category.emoji} ${category.name}") }
            )
        }
    }
}

@Composable
private fun TagFilterRow(
    tags: List<String>,
    selectedTags: Set<String>,
    onToggle: (String) -> Unit,
    onClearFilter: () -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
    ) {
        item {
            FilterChip(
                selected = selectedTags.isEmpty(),
                onClick = onClearFilter,
                label = { Text(stringResource(R.string.filter_all)) }
            )
        }
        items(tags, key = { it }) { tag ->
            FilterChip(
                selected = tag in selectedTags,
                onClick = { onToggle(tag) },
                label = { Text(tag) }
            )
        }
    }
}

@Composable
private fun SortRow(
    selected: TodoSortOption,
    onSelect: (TodoSortOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(sortOptionLabel(selected))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                TodoSortOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(sortOptionLabel(option)) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodoListItem(
    item: TodoItem,
    category: TodoCategory?,
    onToggleCompleted: () -> Unit,
    onClick: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = item.isCompleted, onCheckedChange = { onToggleCompleted() })
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
                    category?.let { cat ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text("${cat.emoji} ${cat.name}", style = MaterialTheme.typography.labelSmall) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            border = null,
                        )
                    }
                    item.tags.forEach { tag ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                            border = null,
                        )
                    }
                }
            }
            IconButton(onClick = onDeleteRequest) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
