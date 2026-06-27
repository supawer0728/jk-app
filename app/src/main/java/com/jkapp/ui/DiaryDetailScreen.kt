package com.jkapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jkapp.data.model.RecordType
import com.jkapp.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryDetailScreen(
    viewModel: DiaryViewModel,
    date: String,
    onBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val record = (uiState as? DiaryUiState.Success)
        ?.data?.records?.find { it.date == date }

    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(record?.let { "${it.date} (${it.dayOfWeek})" } ?: date)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (record != null) {
                        IconButton(onClick = { onNavigateToEdit(date) }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (record == null) {
            Text(
                text = stringResource(R.string.record_not_found),
                modifier = Modifier.padding(innerPadding).padding(16.dp)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (record.recordType) {
                    RecordType.HOSPITAL_VISIT -> {
                        record.hospitalVisit?.let { visit ->
                            DetailSection(title = stringResource(R.string.record_type_hospital)) {
                                DetailField(stringResource(R.string.field_category), visit.category)
                                DetailField(stringResource(R.string.field_details), visit.details)
                                visit.note?.let { note ->
                                    DetailField(stringResource(R.string.field_note), note)
                                }
                            }
                        }
                        record.dailyNotes?.takeIf { it.isNotEmpty() }?.let { notes ->
                            Spacer(modifier = Modifier.height(4.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(4.dp))
                            DetailSection(title = stringResource(R.string.record_type_daily)) {
                                notes.forEachIndexed { i, note ->
                                    if (i > 0) Spacer(modifier = Modifier.height(4.dp))
                                    Text(note, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    RecordType.DAILY_NOTE -> {
                        DetailSection(title = stringResource(R.string.record_type_daily)) {
                            record.dailyNotes?.forEachIndexed { i, note ->
                                if (i > 0) Spacer(modifier = Modifier.height(4.dp))
                                Text(note, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_message, date)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRecord(date)
                    showDeleteDialog = false
                    onBack()
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun DetailField(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
