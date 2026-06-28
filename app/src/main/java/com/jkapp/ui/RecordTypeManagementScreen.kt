package com.jkapp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jkapp.R
import com.jkapp.data.model.CatRecordType

private val PALETTE_COLORS = listOf(
    "#F44336", "#E91E63", "#9C27B0", "#673AB7",
    "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
    "#009688", "#4CAF50", "#8BC34A", "#CDDC39",
    "#FFEB3B", "#FFC107", "#FF9800", "#FF5722",
    "#795548", "#9E9E9E", "#607D8B", "#000000",
    "#FFFFFF",
)

@Composable
private fun ColorPickerField(
    label: String,
    value: String,
    onColorSelected: (String) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val fallbackColor = MaterialTheme.colorScheme.surfaceVariant
    val color = runCatching { Color(value.toColorInt()) }.getOrElse { fallbackColor }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(color, shape = CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable { showPicker = true }
        )
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            modifier = Modifier
                .weight(1f)
                .clickable { showPicker = true },
            trailingIcon = {
                Icon(Icons.Default.Palette, contentDescription = null,
                    modifier = Modifier.clickable { showPicker = true })
            }
        )
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(label) },
            text = {
                LazyVerticalGrid(columns = GridCells.Fixed(4)) {
                    items(PALETTE_COLORS) { hex ->
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .size(40.dp)
                                .background(Color(hex.toColorInt()), CircleShape)
                                .border(
                                    width = if (hex.equals(value, ignoreCase = true)) 3.dp else 1.dp,
                                    color = if (hex.equals(value, ignoreCase = true)) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable {
                                    onColorSelected(hex)
                                    showPicker = false
                                }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordTypeManagementScreen(
    viewModel: DiaryViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.record_type_management_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_record_type))
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)) {
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
                    RecordTypeList(
                        recordTypes = state.recordTypes,
                        onUpdate = { viewModel.updateRecordType(it) },
                        onDelete = { viewModel.deleteRecordType(it) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddRecordTypeDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { newType ->
                viewModel.addRecordType(newType)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun RecordTypeList(
    recordTypes: List<CatRecordType>,
    onUpdate: (CatRecordType) -> Unit,
    onDelete: (String) -> Unit
) {
    val expandedIds = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(recordTypes, key = { it.id }) { type ->
            val isExpanded = expandedIds[type.id] == true
            RecordTypeItem(
                type = type,
                isExpanded = isExpanded,
                onToggleExpand = { expandedIds[type.id] = !isExpanded },
                onUpdate = onUpdate,
                onDelete = onDelete
            )
        }
    }
}

@Composable
private fun RecordTypeItem(
    type: CatRecordType,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onUpdate: (CatRecordType) -> Unit,
    onDelete: (String) -> Unit
) {
    var name by rememberSaveable(type.id) { mutableStateOf(type.name) }
    var emoji by rememberSaveable(type.id) { mutableStateOf(type.emoji) }
    var fontColor by rememberSaveable(type.id) { mutableStateOf(type.fontColor) }
    var backgroundColor by rememberSaveable(type.id) { mutableStateOf(type.backgroundColor) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDeleteRestricted by remember { mutableStateOf(false) }

    LaunchedEffect(type.name, type.emoji, type.fontColor, type.backgroundColor) {
        if (!isExpanded) {
            name = type.name
            emoji = type.emoji
            fontColor = type.fontColor
            backgroundColor = type.backgroundColor
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${type.emoji} ${type.name}",
                    style = MaterialTheme.typography.bodyLarge
                )
                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.record_type_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = it },
                        label = { Text(stringResource(R.string.record_type_emoji)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ColorPickerField(
                        label = stringResource(R.string.record_type_font_color),
                        value = fontColor,
                        onColorSelected = { fontColor = it }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ColorPickerField(
                        label = stringResource(R.string.record_type_bg_color),
                        value = backgroundColor,
                        onColorSelected = { backgroundColor = it }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                if (type.id in DiaryViewModel.SYSTEM_TYPE_IDS) showDeleteRestricted = true
                                else showDeleteConfirm = true
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(stringResource(R.string.delete))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onUpdate(
                                    type.copy(
                                        name = name,
                                        emoji = emoji,
                                        fontColor = fontColor,
                                        backgroundColor = backgroundColor
                                    )
                                )
                            }
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.record_type_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(type.docId.ifBlank { type.id })
                    }
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showDeleteRestricted) {
        AlertDialog(
            onDismissRequest = { showDeleteRestricted = false },
            text = { Text(stringResource(R.string.record_type_delete_restricted)) },
            confirmButton = {
                TextButton(onClick = { showDeleteRestricted = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun AddRecordTypeDialog(
    onDismiss: () -> Unit,
    onConfirm: (CatRecordType) -> Unit
) {
    var id by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var emoji by rememberSaveable { mutableStateOf("📝") }
    var fontColor by rememberSaveable { mutableStateOf("#000000") }
    var backgroundColor by rememberSaveable { mutableStateOf("#FFFFFF") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_record_type)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it },
                    label = { Text("ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.record_type_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it },
                    label = { Text(stringResource(R.string.record_type_emoji)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ColorPickerField(
                    label = stringResource(R.string.record_type_font_color),
                    value = fontColor,
                    onColorSelected = { fontColor = it }
                )
                ColorPickerField(
                    label = stringResource(R.string.record_type_bg_color),
                    value = backgroundColor,
                    onColorSelected = { backgroundColor = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = id.isNotBlank() && name.isNotBlank(),
                onClick = {
                    onConfirm(
                        CatRecordType(
                            id = id.trim(),
                            name = name.trim(),
                            emoji = emoji.trim(),
                            fontColor = fontColor.trim(),
                            backgroundColor = backgroundColor.trim()
                        )
                    )
                }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
