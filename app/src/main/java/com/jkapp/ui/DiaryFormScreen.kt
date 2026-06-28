package com.jkapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jkapp.R
import com.jkapp.data.model.CatRecord

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryFormScreen(
    viewModel: DiaryViewModel,
    firestoreId: String?,
    onBack: () -> Unit
) {
    val isEditMode = firestoreId != null
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val success = uiState as? DiaryUiState.Success
    val recordTypes = success?.recordTypes ?: emptyList()
    val existingRecord = if (isEditMode) success?.records?.find { it.firestoreId == firestoreId } else null

    var recordDate by rememberSaveable { mutableStateOf(existingRecord?.date ?: DiaryViewModel.todayDate()) }
    var selectedTypeId by rememberSaveable { mutableStateOf(existingRecord?.recordType ?: "") }
    var recordText by rememberSaveable { mutableStateOf(existingRecord?.record ?: "") }
    var typeDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(existingRecord) {
        existingRecord?.let { r ->
            recordDate = r.date
            selectedTypeId = r.recordType
            recordText = r.record
        }
    }

    LaunchedEffect(recordTypes) {
        if (selectedTypeId.isEmpty() && recordTypes.isNotEmpty()) {
            selectedTypeId = recordTypes.first().id
        }
    }

    val selectedType = recordTypes.find { it.id == selectedTypeId }
    val isDataReady = !isEditMode || existingRecord != null
    val isValid = isDataReady && recordDate.isNotBlank() && selectedTypeId.isNotEmpty() && recordText.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (isEditMode) R.string.edit_record else R.string.add_record))
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
            OutlinedTextField(
                value = recordDate,
                onValueChange = { recordDate = it },
                label = { Text(stringResource(R.string.field_date)) },
                placeholder = { Text("YYYY-MM-DD") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isEditMode
            )

            ExposedDropdownMenuBox(
                expanded = typeDropdownExpanded,
                onExpandedChange = { typeDropdownExpanded = !typeDropdownExpanded }
            ) {
                val bgColor = selectedType?.backgroundColor?.toComposeColorOrNull()
                    ?: MaterialTheme.colorScheme.surface
                val fontColor = selectedType?.fontColor?.toComposeColorOrNull()
                    ?: MaterialTheme.colorScheme.onSurface

                OutlinedTextField(
                    value = selectedType?.let { "${it.emoji} ${it.name}" } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.record_type_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeDropdownExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedTextColor = fontColor,
                        unfocusedTextColor = fontColor,
                        focusedContainerColor = bgColor.copy(alpha = 0.2f),
                        unfocusedContainerColor = bgColor.copy(alpha = 0.1f)
                    )
                )
                ExposedDropdownMenu(
                    expanded = typeDropdownExpanded,
                    onDismissRequest = { typeDropdownExpanded = false }
                ) {
                    recordTypes.forEach { type ->
                        val itemBgColor = type.backgroundColor.toComposeColorOrNull()
                            ?: MaterialTheme.colorScheme.surface
                        val itemFontColor = type.fontColor.toComposeColorOrNull()
                            ?: MaterialTheme.colorScheme.onSurface

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "${type.emoji} ${type.name}",
                                    color = itemFontColor
                                )
                            },
                            onClick = {
                                selectedTypeId = type.id
                                typeDropdownExpanded = false
                            },
                            modifier = Modifier.background(itemBgColor.copy(alpha = 0.2f))
                        )
                    }
                }
            }

            OutlinedTextField(
                value = recordText,
                onValueChange = { recordText = it },
                label = { Text(stringResource(R.string.field_record)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5
            )

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    val record = CatRecord(
                        firestoreId = firestoreId,
                        date = recordDate,
                        recordType = selectedTypeId,
                        record = recordText.trim()
                    )
                    if (isEditMode) viewModel.updateRecord(record) else viewModel.addRecord(record)
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
