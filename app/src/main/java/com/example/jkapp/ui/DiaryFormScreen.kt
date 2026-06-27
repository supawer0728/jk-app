package com.example.jkapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jkapp.data.model.HealthRecord
import com.example.jkapp.data.model.HospitalVisit
import com.example.jkapp.data.model.RecordType
import com.jkapp.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryFormScreen(
    viewModel: DiaryViewModel,
    date: String?,
    onBack: () -> Unit
) {
    val isEditMode = date != null
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val existingRecord = if (isEditMode) {
        (uiState as? DiaryUiState.Success)?.data?.records?.find { it.date == date }
    } else null

    var selectedType by rememberSaveable {
        mutableStateOf(existingRecord?.recordType ?: RecordType.DAILY_NOTE)
    }
    var recordDate by rememberSaveable {
        mutableStateOf(existingRecord?.date ?: DiaryViewModel.todayDate())
    }
    // Hospital visit fields
    var category by rememberSaveable { mutableStateOf(existingRecord?.hospitalVisit?.category ?: "") }
    var details by rememberSaveable { mutableStateOf(existingRecord?.hospitalVisit?.details ?: "") }
    var hospitalNote by rememberSaveable { mutableStateOf(existingRecord?.hospitalVisit?.note ?: "") }
    // Daily note (lines joined)
    var notesText by rememberSaveable {
        mutableStateOf(existingRecord?.dailyNotes?.joinToString("\n") ?: "")
    }

    // Pre-populate when entering edit mode after state is available
    LaunchedEffect(existingRecord) {
        existingRecord?.let { r ->
            selectedType = r.recordType
            recordDate = r.date
            r.hospitalVisit?.let { v ->
                category = v.category
                details = v.details
                hospitalNote = v.note ?: ""
            }
            r.dailyNotes?.let { notes ->
                notesText = notes.joinToString("\n")
            }
        }
    }

    val isValid = when (selectedType) {
        RecordType.HOSPITAL_VISIT -> category.isNotBlank() && details.isNotBlank()
        RecordType.DAILY_NOTE -> notesText.isNotBlank()
    } && recordDate.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (isEditMode) R.string.edit_record else R.string.add_record
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
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
            // 날짜 입력
            OutlinedTextField(
                value = recordDate,
                onValueChange = { recordDate = it },
                label = { Text(stringResource(R.string.field_date)) },
                placeholder = { Text("YYYY-MM-DD") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isEditMode
            )

            // 타입 선택
            Text(
                text = stringResource(R.string.record_type_label),
                style = MaterialTheme.typography.labelMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedType == RecordType.HOSPITAL_VISIT,
                    onClick = { selectedType = RecordType.HOSPITAL_VISIT },
                    label = { Text(stringResource(R.string.record_type_hospital)) }
                )
                FilterChip(
                    selected = selectedType == RecordType.DAILY_NOTE,
                    onClick = { selectedType = RecordType.DAILY_NOTE },
                    label = { Text(stringResource(R.string.record_type_daily)) }
                )
            }

            // 타입별 입력 필드
            when (selectedType) {
                RecordType.HOSPITAL_VISIT -> {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text(stringResource(R.string.field_category)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = details,
                        onValueChange = { details = it },
                        label = { Text(stringResource(R.string.field_details)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                    OutlinedTextField(
                        value = hospitalNote,
                        onValueChange = { hospitalNote = it },
                        label = { Text(stringResource(R.string.field_note_optional)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
                RecordType.DAILY_NOTE -> {
                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text(stringResource(R.string.field_notes)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        supportingText = { Text(stringResource(R.string.notes_hint)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    val dayOfWeek = DiaryViewModel.computeDayOfWeek(recordDate)
                    val newRecord = HealthRecord(
                        date = recordDate,
                        dayOfWeek = dayOfWeek,
                        recordType = selectedType,
                        hospitalVisit = if (selectedType == RecordType.HOSPITAL_VISIT) {
                            HospitalVisit(
                                category = category.trim(),
                                details = details.trim(),
                                note = hospitalNote.trim().ifBlank { null }
                            )
                        } else null,
                        dailyNotes = if (selectedType == RecordType.DAILY_NOTE) {
                            notesText.lines().map { it.trim() }.filter { it.isNotBlank() }
                        } else existingRecord?.dailyNotes
                    )
                    if (isEditMode) {
                        viewModel.updateRecord(date!!, newRecord)
                    } else {
                        viewModel.addRecord(newRecord)
                    }
                    onBack()
                },
                enabled = isValid,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}
