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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jkapp.R
import com.jkapp.data.model.CatRecord
import com.jkapp.data.model.CatRecordType

@Composable
fun DiaryScreen(
    viewModel: DiaryViewModel,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToAdd: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                val groupedDates = remember(state.records) {
                    state.records
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
                    items(groupedDates, key = { it.first }) { (date, records) ->
                        DiaryListItem(
                            date = date,
                            records = records,
                            recordTypes = state.recordTypes,
                            onClick = { onNavigateToDetail(date) }
                        )
                    }
                }
                FloatingActionButton(
                    onClick = onNavigateToAdd,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_record))
                }
            }
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
                records.take(3).forEach { record ->
                    val type = recordTypes.find { it.id.trim().equals(record.recordType.trim(), ignoreCase = true) }
                    Text(
                        text = "• ${type?.let { "${it.emoji} ${it.name}" } ?: record.recordType}: ${record.record}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (records.size > 3) {
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
    val bgColor = runCatching {
        type?.backgroundColor?.let { Color(it.toColorInt()) }
    }.getOrNull() ?: MaterialTheme.colorScheme.secondaryContainer
    val fontColor = runCatching {
        type?.fontColor?.let { Color(it.toColorInt()) }
    }.getOrNull() ?: MaterialTheme.colorScheme.onSecondaryContainer

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
