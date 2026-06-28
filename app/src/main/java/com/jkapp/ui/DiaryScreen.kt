package com.jkapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jkapp.R
import com.jkapp.data.model.CatRecord
import com.jkapp.data.model.CatRecordType

private const val PREVIEW_RECORD_LIMIT = 3

@Composable
fun DiaryScreen(
    viewModel: DiaryViewModel,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToAdd: () -> Unit,
    onNavigateToRecordTypeManagement: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedTypeIds by viewModel.selectedTypeIds.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is DiaryUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            is DiaryUiState.Error -> {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                )
            }
            is DiaryUiState.Success -> {
                val filteredRecords = remember(state.records, selectedTypeIds) {
                    DiaryViewModel.filterRecords(state.records, selectedTypeIds)
                }
                val groupedDates = remember(filteredRecords) {
                    filteredRecords
                        .groupBy { it.date }
                        .entries
                        .sortedByDescending { it.key }
                        .map { (date, records) -> date to records }
                }
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        RecordTypeFilterRow(
                            recordTypes = state.recordTypes,
                            selectedTypeIds = selectedTypeIds,
                            onToggle = { viewModel.toggleTypeFilter(it) },
                            onClearFilter = { viewModel.clearTypeFilter() }
                        )
                    }
                    items(groupedDates, key = { it.first }) { (date, records) ->
                        DiaryListItem(
                            date = date,
                            records = records,
                            recordTypes = state.recordTypes,
                            onClick = { onNavigateToDetail(date) }
                        )
                    }
                }

                var showFabMenu by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    FloatingActionButton(onClick = { showFabMenu = true }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.manage_record_types))
                    }
                    DropdownMenu(
                        expanded = showFabMenu,
                        onDismissRequest = { showFabMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.write_diary)) },
                            onClick = {
                                showFabMenu = false
                                onNavigateToAdd()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.manage_record_types)) },
                            onClick = {
                                showFabMenu = false
                                onNavigateToRecordTypeManagement()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordTypeFilterRow(
    recordTypes: List<CatRecordType>,
    selectedTypeIds: Set<String>,
    onToggle: (String) -> Unit,
    onClearFilter: () -> Unit
) {
    if (recordTypes.isEmpty()) return
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        item {
            FilterChip(
                selected = selectedTypeIds.isEmpty(),
                onClick = onClearFilter,
                label = { Text(stringResource(R.string.filter_all)) }
            )
        }
        items(recordTypes, key = { it.id }) { type ->
            FilterChip(
                selected = type.id in selectedTypeIds,
                onClick = { onToggle(type.id) },
                label = { Text("${type.emoji} ${type.name}") }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiaryListItem(
    date: String,
    records: List<CatRecord>,
    recordTypes: List<CatRecordType>,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${date} (${DiaryViewModel.computeDayOfWeek(date)})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val uniqueTypeIds = records.map { it.recordType }.distinct()
                    uniqueTypeIds.forEach { typeId ->
                        RecordTypeBadge(
                            type = recordTypes.find { it.id.trim().equals(typeId.trim(), ignoreCase = true) },
                            fallbackId = typeId
                        )
                    }
                }
            }
            if (records.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                records.take(PREVIEW_RECORD_LIMIT).forEach { record ->
                    val type = recordTypes.find { it.id.trim().equals(record.recordType.trim(), ignoreCase = true) }
                    Text(
                        text = "• ${type?.let { "${it.emoji} ${it.name}" } ?: record.recordType}: ${record.record}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (records.size > PREVIEW_RECORD_LIMIT) {
                    Text(
                        text = "...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordTypeBadge(type: CatRecordType?, fallbackId: String) {
    val bgColor = type?.backgroundColor?.toComposeColorOrNull()
        ?: MaterialTheme.colorScheme.secondaryContainer
    val fontColor = type?.fontColor?.toComposeColorOrNull()
        ?: MaterialTheme.colorScheme.onSecondaryContainer

    SuggestionChip(
        onClick = {},
        label = {
            Text(
                text = type?.let { "${it.emoji} ${it.name}" } ?: fallbackId,
                style = MaterialTheme.typography.labelSmall,
                color = fontColor
            )
        },
        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = bgColor),
        border = null
    )
}
