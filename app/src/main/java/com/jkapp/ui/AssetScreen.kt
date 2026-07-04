package com.jkapp.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jkapp.R
import com.jkapp.data.model.AssetItem
import com.jkapp.data.model.DEFAULT_HIDDEN_ASSET_NAMES
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

private val ASSET_OWNERS = listOf("전지훈", "권유경", "공동")

private enum class AssetTab(@StringRes val labelRes: Int) {
    DAILY_ASSET(R.string.asset_tab_daily_asset),
    INVESTMENT(R.string.asset_tab_investment),
    BENCHMARK(R.string.asset_tab_benchmark),
}

@Composable
fun AssetScreen(viewModel: DailyAssetViewModel) {
    var selectedTab by rememberSaveable { mutableStateOf(AssetTab.DAILY_ASSET) }

    Column(modifier = Modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = selectedTab.ordinal) {
            AssetTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = { Text(stringResource(tab.labelRes)) }
                )
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                AssetTab.DAILY_ASSET -> DailyAssetTab(viewModel = viewModel)
                AssetTab.INVESTMENT -> PlaceholderTabContent(stringResource(R.string.asset_tab_investment_placeholder))
                AssetTab.BENCHMARK -> PlaceholderTabContent(stringResource(R.string.asset_tab_benchmark_placeholder))
            }
        }
    }
}

@Composable
private fun PlaceholderTabContent(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message)
    }
}

private data class AssetPendingDelete(val date: String, val target: AssetItem)

// 자산 입력 폼이 "신규 추가"인지 "기존 항목 수정"인지, 수정이라면 어떤 항목인지를 하나의 상태로 표현한다.
// (이전에는 showForm: Boolean과 editingIndex: Int?를 별도로 관리해, 진입점마다 두 값을 함께
// 갱신해야 했고 dismiss 시 editingIndex가 초기화되지 않아 값이 잔류하는 구조였다.)
private sealed interface AssetFormTarget {
    data object New : AssetFormTarget
    data class Edit(val target: AssetItem) : AssetFormTarget
}

@Composable
private fun DailyAssetTab(viewModel: DailyAssetViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedDate by rememberSaveable { mutableStateOf<String?>(null) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    // AssetItem은 Parcelable/Serializable이 아니므로 rememberSaveable로 저장할 수 없다(회전 시 초기화됨).
    var formTarget by remember { mutableStateOf<AssetFormTarget?>(null) }
    var showFabMenu by remember { mutableStateOf(false) }
    var showPasteImport by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<AssetPendingDelete?>(null) }
    var selectedOwners by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    var showHidden by rememberSaveable { mutableStateOf(false) }

    val success = uiState as? DailyAssetUiState.Success
    val availableDates = remember(success) {
        success?.dailyAssets?.map { it.date }?.sortedDescending() ?: emptyList()
    }

    LaunchedEffect(availableDates) {
        if (selectedDate == null || selectedDate !in availableDates) {
            selectedDate = availableDates.firstOrNull()
        }
    }

    val currentDailyAsset = remember(success, selectedDate) {
        success?.dailyAssets?.find { it.date == selectedDate }
    }
    // 필터 칩은 고정된 ASSET_OWNERS에 더해, 과거 데이터 등으로 그 외의 명의 값이 존재하면 함께 노출한다.
    val ownerFilterOptions = remember(currentDailyAsset) {
        ASSET_OWNERS + currentDailyAsset?.assets.orEmpty().map { it.owner }.filter { it !in ASSET_OWNERS }.distinct()
    }
    val groupedAssets = remember(currentDailyAsset, selectedOwners, showHidden) {
        currentDailyAsset?.assets.orEmpty()
            .withIndex()
            .filter { (selectedOwners.isEmpty() || it.value.owner in selectedOwners) && (showHidden || !it.value.hidden) }
            .groupBy({ it.value.owner }, { it })
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is DailyAssetUiState.Loading -> {
                LoadingIndicator(modifier = Modifier.align(Alignment.Center))
            }
            is DailyAssetUiState.Error -> {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
            }
            is DailyAssetUiState.Success -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    DateNavigatorBar(
                        selectedDate = selectedDate,
                        availableDates = availableDates,
                        onSelectDate = { selectedDate = it },
                        onPickNewDate = { showDatePicker = true },
                    )
                    OwnerFilterRow(
                        owners = ownerFilterOptions,
                        selectedOwners = selectedOwners,
                        onToggle = { owner ->
                            selectedOwners = if (owner in selectedOwners) selectedOwners - owner else selectedOwners + owner
                        },
                        onClearFilter = { selectedOwners = emptySet() },
                        showHidden = showHidden,
                        onToggleShowHidden = { showHidden = !showHidden },
                    )
                    if (groupedAssets.isEmpty()) {
                        // 명의 필터로 인해 목록이 비었는지, 아니면 해당 날짜에 자산 자체가 없는지 구분해 안내한다.
                        val emptyByFilter = selectedOwners.isNotEmpty() && !currentDailyAsset?.assets.isNullOrEmpty()
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(if (emptyByFilter) R.string.asset_empty_filtered else R.string.asset_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            groupedAssets.forEach { (owner, indexedAssets) ->
                                item(key = "header-$owner") {
                                    Text(owner, style = MaterialTheme.typography.titleSmall)
                                }
                                items(indexedAssets, key = { "${owner}-${it.index}" }) { (_, asset) ->
                                    AssetListItem(
                                        asset = asset,
                                        onEditRequest = {
                                            formTarget = AssetFormTarget.Edit(asset)
                                        },
                                        onDeleteRequest = {
                                            pendingDelete = AssetPendingDelete(date = selectedDate ?: return@AssetListItem, target = asset)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                Box(modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                    FloatingActionButton(onClick = { showFabMenu = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.asset_add))
                    }
                    DropdownMenu(expanded = showFabMenu, onDismissRequest = { showFabMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.asset_add_individual)) },
                            onClick = {
                                showFabMenu = false
                                formTarget = AssetFormTarget.New
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.asset_add_paste)) },
                            onClick = {
                                showFabMenu = false
                                showPasteImport = true
                            },
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        IsoDatePickerDialog(
            initialDate = selectedDate,
            onDismiss = { showDatePicker = false },
            onConfirm = { date ->
                selectedDate = date
                showDatePicker = false
            },
        )
    }

    formTarget?.let { target ->
        // 자산이 하나도 없어 선택된 날짜가 없을 때는 오늘 날짜로 첫 문서를 생성한다.
        val dateForForm = selectedDate ?: DiaryViewModel.todayDate()
        AssetFormDialog(
            initial = (target as? AssetFormTarget.Edit)?.target,
            onDismiss = { formTarget = null },
            onSave = { item ->
                when (target) {
                    is AssetFormTarget.Edit -> viewModel.updateAsset(dateForForm, target.target, item)
                    AssetFormTarget.New -> viewModel.addAsset(dateForForm, item)
                }
                formTarget = null
            },
        )
    }

    if (showPasteImport) {
        // 자산이 하나도 없어 선택된 날짜가 없을 때는 오늘 날짜로 첫 문서를 생성한다.
        val dateForImport = selectedDate ?: DiaryViewModel.todayDate()
        AssetPasteImportDialog(
            onDismiss = { showPasteImport = false },
            onParse = viewModel::parsePasteText,
            onImport = { items ->
                viewModel.importAssets(dateForImport, items)
                showPasteImport = false
            },
        )
    }

    pendingDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.asset_delete_confirm_title)) },
            text = { Text(stringResource(R.string.asset_delete_confirm_message, pending.target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAsset(pending.date, pending.target)
                    pendingDelete = null
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun DateNavigatorBar(
    selectedDate: String?,
    availableDates: List<String>,
    onSelectDate: (String) -> Unit,
    onPickNewDate: () -> Unit,
) {
    var showDropdown by remember { mutableStateOf(false) }
    val index = availableDates.indexOf(selectedDate)
    val hasPrevious = index >= 0 && index < availableDates.lastIndex
    val hasNext = index > 0

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(
            onClick = { availableDates.getOrNull(index + 1)?.let(onSelectDate) },
            enabled = hasPrevious,
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous_date))
        }
        Box {
            TextButton(onClick = { showDropdown = true }, enabled = availableDates.isNotEmpty()) {
                Text(
                    text = selectedDate ?: stringResource(R.string.asset_no_date),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            DropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }) {
                availableDates.forEach { date ->
                    DropdownMenuItem(
                        text = { Text(date) },
                        onClick = {
                            onSelectDate(date)
                            showDropdown = false
                        },
                    )
                }
            }
        }
        IconButton(
            onClick = { availableDates.getOrNull(index - 1)?.let(onSelectDate) },
            enabled = hasNext,
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_date))
        }
        IconButton(onClick = onPickNewDate) {
            Icon(Icons.Default.DateRange, contentDescription = stringResource(R.string.asset_pick_date))
        }
    }
}

@Composable
private fun OwnerFilterRow(
    owners: List<String>,
    selectedOwners: Set<String>,
    onToggle: (String) -> Unit,
    onClearFilter: () -> Unit,
    showHidden: Boolean,
    onToggleShowHidden: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
        ) {
            item {
                FilterChip(
                    selected = selectedOwners.isEmpty(),
                    onClick = onClearFilter,
                    label = { Text(stringResource(R.string.filter_all)) },
                )
            }
            items(owners) { owner ->
                FilterChip(
                    selected = owner in selectedOwners,
                    onClick = { onToggle(owner) },
                    label = { Text(owner) },
                )
            }
        }
        IconButton(onClick = onToggleShowHidden) {
            Icon(
                imageVector = if (showHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = stringResource(R.string.asset_toggle_show_hidden),
                tint = if (showHidden) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AssetListItem(
    asset: AssetItem,
    onEditRequest: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(asset.name, style = MaterialTheme.typography.bodyLarge)
                    if (asset.hidden) {
                        Icon(
                            Icons.Default.VisibilityOff,
                            contentDescription = stringResource(R.string.asset_hidden),
                            modifier = Modifier.padding(start = 4.dp).size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val details = listOfNotNull(
                    asset.institution?.takeIf { it.isNotBlank() },
                    asset.accountNumber?.takeIf { it.isNotBlank() }?.let { "계좌 $it" },
                    asset.card?.takeIf { it.isNotBlank() }?.let { "카드 $it" },
                ).joinToString(" · ")
                if (details.isNotEmpty()) {
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                asset.amount?.let {
                    Text(text = it.toDisplayAmount(), style = MaterialTheme.typography.bodyMedium)
                }
            }
            IconButton(onClick = onEditRequest) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
            }
            IconButton(onClick = onDeleteRequest) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
            }
        }
    }
}

fun BigDecimal.toDisplayAmount(): String =
    "${NumberFormat.getNumberInstance(Locale.KOREA).format(this)}원"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssetFormDialog(
    initial: AssetItem?,
    onDismiss: () -> Unit,
    onSave: (AssetItem) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var owner by rememberSaveable { mutableStateOf(initial?.owner ?: ASSET_OWNERS.first()) }
    var ownerDropdownExpanded by remember { mutableStateOf(false) }
    var institution by rememberSaveable { mutableStateOf(initial?.institution ?: "") }
    var accountNumber by rememberSaveable { mutableStateOf(initial?.accountNumber ?: "") }
    var card by rememberSaveable { mutableStateOf(initial?.card ?: "") }
    var amountText by rememberSaveable { mutableStateOf(initial?.amount?.toPlainString() ?: "") }
    var hidden by rememberSaveable { mutableStateOf(initial?.hidden ?: false) }
    var hiddenManuallySet by rememberSaveable { mutableStateOf(false) }

    // 신규 항목이고 사용자가 숨김 여부를 직접 건드리지 않았다면, 이름이 기본 숨김 목록과 일치할 때 자동으로 체크한다.
    LaunchedEffect(name) {
        if (initial == null && !hiddenManuallySet) {
            hidden = name.trim() in DEFAULT_HIDDEN_ASSET_NAMES
        }
    }

    val amountValid = amountText.isBlank() || amountText.toBigDecimalOrNull() != null
    val isValid = name.isNotBlank() && owner.isNotBlank() && amountValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial != null) R.string.asset_edit_title else R.string.asset_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.asset_field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                ExposedDropdownMenuBox(
                    expanded = ownerDropdownExpanded,
                    onExpandedChange = { ownerDropdownExpanded = !ownerDropdownExpanded },
                ) {
                    OutlinedTextField(
                        value = owner,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.asset_field_owner)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = ownerDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = ownerDropdownExpanded,
                        onDismissRequest = { ownerDropdownExpanded = false },
                    ) {
                        ASSET_OWNERS.forEach { candidate ->
                            DropdownMenuItem(
                                text = { Text(candidate) },
                                onClick = {
                                    owner = candidate
                                    ownerDropdownExpanded = false
                                },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = institution,
                    onValueChange = { institution = it },
                    label = { Text(stringResource(R.string.asset_field_institution)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = accountNumber,
                    onValueChange = { accountNumber = it },
                    label = { Text(stringResource(R.string.asset_field_account_number)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = card,
                    onValueChange = { card = it },
                    label = { Text(stringResource(R.string.asset_field_card)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(stringResource(R.string.asset_field_amount)) },
                    singleLine = true,
                    isError = !amountValid,
                    supportingText = {
                        if (!amountValid) Text(stringResource(R.string.asset_amount_invalid))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = hidden,
                        onCheckedChange = {
                            hidden = it
                            hiddenManuallySet = true
                        },
                    )
                    Text(stringResource(R.string.asset_field_hidden))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        AssetItem(
                            name = name.trim(),
                            owner = owner,
                            institution = institution.trim().ifBlank { null },
                            accountNumber = accountNumber.trim().ifBlank { null },
                            card = card.trim().ifBlank { null },
                            amount = amountText.trim().ifBlank { null }?.toBigDecimalOrNull(),
                            hidden = hidden,
                        )
                    )
                },
                enabled = isValid,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun AssetPasteImportDialog(
    onDismiss: () -> Unit,
    onParse: (text: String, hasHeader: Boolean) -> List<ParsedAssetRow>,
    onImport: (List<AssetItem>) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var hasHeader by rememberSaveable { mutableStateOf(true) }
    var parsed by remember { mutableStateOf<List<ParsedAssetRow>?>(null) }

    val result = parsed

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.asset_paste_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (result == null) {
                    Text(
                        text = stringResource(R.string.asset_paste_import_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text(stringResource(R.string.asset_paste_import_field)) },
                        minLines = 8,
                        maxLines = 12,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = hasHeader, onCheckedChange = { hasHeader = it })
                        Text(stringResource(R.string.asset_paste_import_has_header))
                    }
                } else {
                    val validCount = result.count { it.item != null }
                    val errorCount = result.size - validCount
                    Text(
                        text = stringResource(R.string.asset_paste_import_summary, validCount, errorCount),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(result) { row ->
                            val item = row.item
                            if (item != null) {
                                Text(
                                    text = "${item.owner} · ${item.name}" +
                                        (item.amount?.let { " · ${it.toDisplayAmount()}" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else {
                                Text(
                                    text = "⚠ ${row.error}: ${row.rawLine.take(30)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (result == null) {
                TextButton(
                    onClick = { parsed = onParse(text, hasHeader) },
                    enabled = text.isNotBlank(),
                ) { Text(stringResource(R.string.asset_paste_import_parse)) }
            } else {
                TextButton(
                    onClick = { onImport(result.mapNotNull { it.item }) },
                    enabled = result.any { it.item != null },
                ) { Text(stringResource(R.string.save)) }
            }
        },
        dismissButton = {
            if (result == null) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            } else {
                TextButton(onClick = { parsed = null }) { Text(stringResource(R.string.asset_paste_import_back)) }
            }
        }
    )
}
