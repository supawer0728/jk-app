package com.jkapp.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jkapp.R
import com.jkapp.data.model.AssetItem
import com.jkapp.data.model.Benchmark
import com.jkapp.data.model.BenchmarkRowMetrics
import com.jkapp.data.model.DEFAULT_HIDDEN_ASSET_NAMES
import com.jkapp.data.model.InvestmentItem
import com.jkapp.data.model.InvestmentItemMetrics
import com.jkapp.data.model.PurchasePrice
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

private enum class AssetTab(@StringRes val labelRes: Int) {
    DAILY_ASSET(R.string.asset_tab_daily_asset),
    INVESTMENT(R.string.asset_tab_investment),
    BENCHMARK(R.string.asset_tab_benchmark),
}

@Composable
fun AssetScreen(
    viewModel: DailyAssetViewModel,
    investmentViewModel: DailyAssetInvestmentViewModel,
    benchmarkViewModel: BenchmarkViewModel,
) {
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
                AssetTab.INVESTMENT -> InvestmentTab(viewModel = investmentViewModel)
                AssetTab.BENCHMARK -> BenchmarkTab(viewModel = benchmarkViewModel)
            }
        }
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
    // 아래 파생 State들은 DailyAssetViewModel에서 데이터가 실제로 바뀔 때만 계산되어 캐시된다.
    // 여기서 remember로 다시 계산하면 탭을 오갈 때마다 컴포지션이 새로 생성되면서 매번
    // 재계산되므로(이슈 #37), 뷰모델의 StateFlow를 그대로 구독한다.
    val availableDates by viewModel.availableDates.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val currentDailyAsset by viewModel.currentDailyAsset.collectAsStateWithLifecycle()
    val ownerFilterOptions by viewModel.ownerFilterOptions.collectAsStateWithLifecycle()
    val selectedOwners by viewModel.selectedOwners.collectAsStateWithLifecycle()
    val showHidden by viewModel.showHidden.collectAsStateWithLifecycle()
    val groupedAssets by viewModel.groupedAssets.collectAsStateWithLifecycle()

    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    // AssetItem은 Parcelable/Serializable이 아니므로 rememberSaveable로 저장할 수 없다(회전 시 초기화됨).
    var formTarget by remember { mutableStateOf<AssetFormTarget?>(null) }
    var showFabMenu by remember { mutableStateOf(false) }
    var showPasteImport by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<AssetPendingDelete?>(null) }

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
                        onSelectDate = viewModel::selectDate,
                        onPickNewDate = { showDatePicker = true },
                    )
                    OwnerFilterRow(
                        owners = ownerFilterOptions,
                        selectedOwners = selectedOwners,
                        onToggle = viewModel::toggleOwnerFilter,
                        onClearFilter = viewModel::clearOwnerFilter,
                        showHidden = showHidden,
                        onToggleShowHidden = viewModel::toggleShowHidden,
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
                viewModel.selectDate(date)
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
    var hasHeader by rememberSaveable { mutableStateOf(true) }

    PasteImportDialog(
        title = stringResource(R.string.asset_paste_import_title),
        description = stringResource(R.string.asset_paste_import_description),
        extraOptions = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = hasHeader, onCheckedChange = { hasHeader = it })
                Text(stringResource(R.string.asset_paste_import_has_header))
            }
        },
        onDismiss = onDismiss,
        onParse = { text -> onParse(text, hasHeader) },
        itemOf = { it.item },
        errorOf = { it.error },
        rawLineOf = { it.rawLine },
        onImport = onImport,
    ) { item ->
        Text(
            text = "${item.owner} · ${item.name}" + (item.amount?.let { " · ${it.toDisplayAmount()}" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

// 텍스트 붙여넣기 → 파싱 → 미리보기 → 확인 후 저장이라는 공통 흐름을 자산/벤치마크 붙여넣기 다이얼로그가
// 함께 사용한다. hasHeader 체크박스(extraOptions)나 미리보기 행 스타일처럼 다른 부분만 콜백으로 뺀다.
@Composable
private fun <T, R> PasteImportDialog(
    title: String,
    description: String,
    extraOptions: (@Composable () -> Unit)? = null,
    onDismiss: () -> Unit,
    onParse: (text: String) -> List<R>,
    itemOf: (R) -> T?,
    errorOf: (R) -> String?,
    rawLineOf: (R) -> String,
    onImport: (List<T>) -> Unit,
    rowContent: @Composable (T) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var parsed by remember { mutableStateOf<List<R>?>(null) }

    val result = parsed

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (result == null) {
                    Text(
                        text = description,
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
                    extraOptions?.invoke()
                } else {
                    val validCount = result.count { itemOf(it) != null }
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
                            val item = itemOf(row)
                            if (item != null) {
                                rowContent(item)
                            } else {
                                Text(
                                    text = "⚠ ${errorOf(row)}: ${rawLineOf(row).take(30)}",
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
                    onClick = { parsed = onParse(text) },
                    enabled = text.isNotBlank(),
                ) { Text(stringResource(R.string.asset_paste_import_parse)) }
            } else {
                TextButton(
                    onClick = { onImport(result.mapNotNull(itemOf)) },
                    enabled = result.any { itemOf(it) != null },
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

private data class InvestmentPendingDelete(val date: String, val owner: String, val target: InvestmentItem)

private sealed interface InvestmentFormTarget {
    data object New : InvestmentFormTarget
    data class Edit(val target: InvestmentItem) : InvestmentFormTarget
}

private val INVESTMENT_CURRENCIES = listOf("KRW", "USD")

@Composable
private fun InvestmentTab(viewModel: DailyAssetInvestmentViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    // 아래 파생 State들은 DailyAssetInvestmentViewModel에서 데이터가 실제로 바뀔 때만 계산되어
    // 캐시된다. 여기서 remember로 다시 계산하면 탭을 오갈 때마다 컴포지션이 새로 생성되면서 매번
    // 재계산되므로(이슈 #37), 뷰모델의 StateFlow를 그대로 구독한다.
    val availableDates by viewModel.availableDates.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val selectedOwner by viewModel.selectedOwner.collectAsStateWithLifecycle()
    val rowMetrics by viewModel.investmentRowMetrics.collectAsStateWithLifecycle()

    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    // InvestmentItem은 Parcelable/Serializable이 아니므로 rememberSaveable로 저장할 수 없다(회전 시 초기화됨).
    var formTarget by remember { mutableStateOf<InvestmentFormTarget?>(null) }
    var pendingDelete by remember { mutableStateOf<InvestmentPendingDelete?>(null) }
    var showFabMenu by remember { mutableStateOf(false) }
    var showPasteImport by rememberSaveable { mutableStateOf(false) }
    var isSelectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf<Set<InvestmentItem>>(emptySet()) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // 선택 모드에 들어가면 맨 위(첫 항목)부터 볼 수 있도록 목록을 위로 스크롤한다(BenchmarkTab과 동일한 패턴).
    LaunchedEffect(isSelectionMode) {
        if (isSelectionMode) listState.animateScrollToItem(0)
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedItems = emptySet()
    }

    // 명의나 날짜를 바꾸면 화면에 보이는 목록 자체가 바뀌므로, 선택 모드에서 체크해 둔 항목이
    // 더 이상 보이는 목록과 무관해진다(엉뚱한 항목 삭제 방지). 전환 시 선택 모드를 초기화한다.
    LaunchedEffect(selectedOwner, selectedDate) {
        exitSelectionMode()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is DailyAssetInvestmentUiState.Loading -> {
                LoadingIndicator(modifier = Modifier.align(Alignment.Center))
            }
            is DailyAssetInvestmentUiState.Error -> {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
            }
            is DailyAssetInvestmentUiState.Success -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    DateNavigatorBar(
                        selectedDate = selectedDate,
                        availableDates = availableDates,
                        onSelectDate = viewModel::selectDate,
                        onPickNewDate = { showDatePicker = true },
                    )
                    InvestmentOwnerTabRow(
                        owners = INVESTMENT_OWNERS,
                        selectedOwner = selectedOwner,
                        onSelect = viewModel::selectOwner,
                    )
                    if (rowMetrics.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.investment_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = listState,
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(rowMetrics, key = { it.item }) { entry ->
                                InvestmentListItem(
                                    entry = entry,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = entry.item in selectedItems,
                                    onToggleSelected = {
                                        selectedItems = if (entry.item in selectedItems) {
                                            selectedItems - entry.item
                                        } else {
                                            selectedItems + entry.item
                                        }
                                    },
                                    onEditRequest = { formTarget = InvestmentFormTarget.Edit(entry.item) },
                                    onDeleteRequest = {
                                        val date = selectedDate
                                        if (date != null) {
                                            pendingDelete = InvestmentPendingDelete(date = date, owner = selectedOwner, target = entry.item)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                if (isSelectionMode) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { showDeleteAllConfirm = true },
                            enabled = rowMetrics.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.investment_delete_all)) }
                        Button(
                            onClick = {
                                val date = selectedDate
                                if (date != null) {
                                    viewModel.deleteInvestments(date, selectedOwner, selectedItems.toList())
                                }
                                exitSelectionMode()
                            },
                            enabled = selectedItems.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.investment_delete_selected)) }
                        OutlinedButton(
                            onClick = { exitSelectionMode() },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.cancel)) }
                    }
                } else {
                    Box(modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box {
                                FloatingActionButton(onClick = { showFabMenu = true }) {
                                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.investment_add))
                                }
                                DropdownMenu(expanded = showFabMenu, onDismissRequest = { showFabMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.asset_add_individual)) },
                                        onClick = {
                                            showFabMenu = false
                                            formTarget = InvestmentFormTarget.New
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
                            if (rowMetrics.isNotEmpty()) {
                                FloatingActionButton(onClick = { isSelectionMode = true }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.investment_bulk_delete))
                                }
                            }
                        }
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
                viewModel.selectDate(date)
                showDatePicker = false
            },
        )
    }

    formTarget?.let { target ->
        // 신규 입력은 기본 날짜를 오늘로 하고 사용자가 DatePicker로 바꿀 수 있다. 수정은 항목이
        // 이미 속한 날짜(현재 화면에 표시 중인 날짜)를 그대로 쓰고 바꿀 수 없다(문서 이동 미지원).
        val editTarget = target as? InvestmentFormTarget.Edit
        val initialDate = if (editTarget != null) selectedDate ?: DiaryViewModel.todayDate() else DiaryViewModel.todayDate()
        InvestmentFormDialog(
            initial = editTarget?.target,
            initialDate = initialDate,
            onDismiss = { formTarget = null },
            onSave = { date, item ->
                when (target) {
                    is InvestmentFormTarget.Edit -> viewModel.updateInvestment(date, selectedOwner, target.target, item)
                    InvestmentFormTarget.New -> viewModel.addInvestment(date, selectedOwner, item)
                }
                formTarget = null
            },
        )
    }

    if (showPasteImport) {
        InvestmentPasteImportDialog(
            initialOwner = selectedOwner,
            initialDate = DiaryViewModel.todayDate(),
            onDismiss = { showPasteImport = false },
            onParse = viewModel::parsePasteText,
            onImport = { date, owner, items ->
                viewModel.importInvestments(date, owner, items)
                showPasteImport = false
            },
        )
    }

    actionError?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.consumeActionError() },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.consumeActionError() }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }

    pendingDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.investment_delete_confirm_title)) },
            text = { Text(stringResource(R.string.investment_delete_confirm_message, pending.target.investmentName)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteInvestment(pending.date, pending.owner, pending.target)
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

    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text(stringResource(R.string.investment_delete_all_confirm_title)) },
            text = { Text(stringResource(R.string.investment_delete_all_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    val date = selectedDate
                    if (date != null) {
                        viewModel.deleteInvestments(date, selectedOwner, rowMetrics.map { it.item })
                    }
                    showDeleteAllConfirm = false
                    exitSelectionMode()
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun InvestmentOwnerTabRow(owners: List<String>, selectedOwner: String, onSelect: (String) -> Unit) {
    SecondaryTabRow(selectedTabIndex = owners.indexOf(selectedOwner).coerceAtLeast(0)) {
        owners.forEach { owner ->
            Tab(
                selected = owner == selectedOwner,
                onClick = { onSelect(owner) },
                text = { Text(owner) },
            )
        }
    }
}

@Composable
private fun InvestmentListItem(
    entry: InvestmentItemMetrics,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelected: () -> Unit,
    onEditRequest: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    val item = entry.item
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (isSelectionMode) it.clickable(onClick = onToggleSelected) else it },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSelectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onToggleSelected() })
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(item.investmentName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "${item.assetName} · ${item.category}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${item.quantity.toPlainString()}주 · 1주 ${item.pricePerShare.toDisplayAmount()} · 매수단가 ${item.purchasePrice.toDisplayString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = item.valuationAmount.toDisplayAmount(), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "${stringResource(R.string.investment_field_profit)} ${entry.profit.toDisplayAmount()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = signColor(entry.profit),
                )
            }
            if (!isSelectionMode) {
                IconButton(onClick = onEditRequest) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                }
                IconButton(onClick = onDeleteRequest) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                }
            }
        }
    }
}

private fun PurchasePrice.toDisplayString(): String = when (currency) {
    "KRW" -> amount.toDisplayAmount()
    else -> "${NumberFormat.getNumberInstance(Locale.US).format(amount)} $currency"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InvestmentFormDialog(
    initial: InvestmentItem?,
    initialDate: String,
    onDismiss: () -> Unit,
    onSave: (date: String, item: InvestmentItem) -> Unit,
) {
    var date by rememberSaveable { mutableStateOf(initialDate) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var assetName by rememberSaveable { mutableStateOf(initial?.assetName ?: "") }
    var category by rememberSaveable { mutableStateOf(initial?.category ?: "") }
    var investmentName by rememberSaveable { mutableStateOf(initial?.investmentName ?: "") }
    var pricePerShareText by rememberSaveable { mutableStateOf(initial?.pricePerShare?.toPlainString() ?: "") }
    var valuationAmountText by rememberSaveable { mutableStateOf(initial?.valuationAmount?.toPlainString() ?: "") }
    var currency by rememberSaveable { mutableStateOf(initial?.purchasePrice?.currency ?: INVESTMENT_CURRENCIES.first()) }
    var currencyDropdownExpanded by remember { mutableStateOf(false) }
    var purchasePriceAmountText by rememberSaveable { mutableStateOf(initial?.purchasePrice?.amount?.toPlainString() ?: "") }
    var quantityText by rememberSaveable { mutableStateOf(initial?.quantity?.toPlainString() ?: "") }
    var purchaseAmountText by rememberSaveable { mutableStateOf(initial?.purchaseAmount?.toPlainString() ?: "") }

    val pricePerShare = pricePerShareText.trim().toBigDecimalOrNull()
    val valuationAmount = valuationAmountText.trim().toBigDecimalOrNull()
    val purchasePriceAmount = purchasePriceAmountText.trim().toBigDecimalOrNull()
    val quantity = quantityText.trim().toBigDecimalOrNull()
    val purchaseAmount = purchaseAmountText.trim().toBigDecimalOrNull()
    val isValid = assetName.isNotBlank() && category.isNotBlank() && investmentName.isNotBlank() &&
        pricePerShare != null && valuationAmount != null && purchasePriceAmount != null &&
        quantity != null && purchaseAmount != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial != null) R.string.investment_edit_title else R.string.investment_add_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 날짜는 문서 ID({date}_{owner})의 일부라 수정 중에는 바꿀 수 없다(항목을 다른
                // 날짜 문서로 옮기는 것은 지원하지 않는다). 신규 입력은 오늘 날짜가 기본값이다.
                OutlinedTextField(
                    value = date,
                    onValueChange = {},
                    readOnly = true,
                    enabled = initial == null,
                    label = { Text(stringResource(R.string.benchmark_field_date)) },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }, enabled = initial == null) {
                            Icon(Icons.Default.DateRange, contentDescription = stringResource(R.string.asset_pick_date))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = assetName,
                    onValueChange = { assetName = it },
                    label = { Text(stringResource(R.string.investment_field_asset_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text(stringResource(R.string.investment_field_category)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = investmentName,
                    onValueChange = { investmentName = it },
                    label = { Text(stringResource(R.string.investment_field_investment_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                BenchmarkNumberField(pricePerShareText, { pricePerShareText = it }, R.string.investment_field_price_per_share, pricePerShare != null)
                BenchmarkNumberField(valuationAmountText, { valuationAmountText = it }, R.string.investment_field_valuation_amount, valuationAmount != null)
                ExposedDropdownMenuBox(
                    expanded = currencyDropdownExpanded,
                    onExpandedChange = { currencyDropdownExpanded = !currencyDropdownExpanded },
                ) {
                    OutlinedTextField(
                        value = currency,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.investment_field_purchase_price_currency)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = currencyDropdownExpanded,
                        onDismissRequest = { currencyDropdownExpanded = false },
                    ) {
                        INVESTMENT_CURRENCIES.forEach { candidate ->
                            DropdownMenuItem(
                                text = { Text(candidate) },
                                onClick = {
                                    currency = candidate
                                    currencyDropdownExpanded = false
                                },
                            )
                        }
                    }
                }
                BenchmarkNumberField(purchasePriceAmountText, { purchasePriceAmountText = it }, R.string.investment_field_purchase_price_amount, purchasePriceAmount != null)
                BenchmarkNumberField(quantityText, { quantityText = it }, R.string.investment_field_quantity, quantity != null)
                BenchmarkNumberField(purchaseAmountText, { purchaseAmountText = it }, R.string.investment_field_purchase_amount, purchaseAmount != null)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        date,
                        InvestmentItem(
                            assetName = assetName.trim(),
                            category = category.trim(),
                            investmentName = investmentName.trim(),
                            pricePerShare = pricePerShare!!,
                            valuationAmount = valuationAmount!!,
                            purchasePrice = PurchasePrice(currency = currency, amount = purchasePriceAmount!!),
                            quantity = quantity!!,
                            purchaseAmount = purchaseAmount!!,
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

    if (showDatePicker) {
        IsoDatePickerDialog(
            initialDate = date,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                date = it
                showDatePicker = false
            },
        )
    }
}

// 구글시트 붙여넣기와 개별 입력이 날짜/명의를 다루는 방식이 다르다: 개별 입력은 현재 화면에
// 표시 중인 명의 탭(selectedOwner)에 저장되지만, 붙여넣기 시트에는 명의 열이 없어 다이얼로그
// 자체에서 명의를 선택하게 한다(요구사항: "구글시트 붙여넣기할 때 어떤 명의의 투자종목인지
// 선택할 수 있다"). 날짜도 마찬가지로 시트에 열이 없어 다이얼로그에서 직접 고르며, 기본값은
// 오늘 날짜다.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InvestmentPasteImportDialog(
    initialOwner: String,
    initialDate: String,
    onDismiss: () -> Unit,
    onParse: (text: String, owner: String) -> List<ParsedInvestmentRow>,
    onImport: (date: String, owner: String, items: List<InvestmentItem>) -> Unit,
) {
    var owner by rememberSaveable { mutableStateOf(initialOwner) }
    var ownerDropdownExpanded by remember { mutableStateOf(false) }
    var date by rememberSaveable { mutableStateOf(initialDate) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    PasteImportDialog(
        title = stringResource(R.string.investment_paste_import_title),
        description = stringResource(R.string.investment_paste_import_description),
        extraOptions = {
            OutlinedTextField(
                value = date,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.benchmark_field_date)) },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = stringResource(R.string.asset_pick_date))
                    }
                },
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
                    INVESTMENT_OWNERS.forEach { candidate ->
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
        },
        onDismiss = onDismiss,
        onParse = { text -> onParse(text, owner) },
        itemOf = { it.item },
        errorOf = { it.error },
        rawLineOf = { it.rawLine },
        onImport = { items -> onImport(date, owner, items) },
    ) { item ->
        Text(
            text = "${item.assetName} · ${item.investmentName} · ${item.valuationAmount.toDisplayAmount()}",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    if (showDatePicker) {
        IsoDatePickerDialog(
            initialDate = date,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                date = it
                showDatePicker = false
            },
        )
    }
}

private sealed interface BenchmarkFormTarget {
    data object New : BenchmarkFormTarget
    data class Edit(val benchmark: Benchmark) : BenchmarkFormTarget
}

private val BENCHMARK_DATE_COLUMN_WIDTH = 96.dp
private val BENCHMARK_VALUE_COLUMN_WIDTH = 104.dp
private val BENCHMARK_PERCENT_COLUMN_WIDTH = 84.dp
private val BENCHMARK_ACTION_COLUMN_WIDTH = 88.dp
private val BENCHMARK_CHECKBOX_COLUMN_WIDTH = 40.dp

@Composable
private fun BenchmarkTab(viewModel: BenchmarkViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    // 20개 열의 파생 지표는 뷰모델(BenchmarkViewModel.rowMetrics)에서 데이터가 실제로 바뀔 때만
    // 계산되어 캐시된다. 여기서 remember로 다시 계산하면 탭을 오갈 때마다 컴포지션이 새로
    // 생성되면서 매번 재계산되므로, 뷰모델의 StateFlow를 그대로 구독한다.
    val entries by viewModel.rowMetrics.collectAsStateWithLifecycle()
    // Benchmark는 Parcelable/Serializable이 아니므로 rememberSaveable로 저장할 수 없다(회전 시 초기화됨).
    var formTarget by remember { mutableStateOf<BenchmarkFormTarget?>(null) }
    var pendingDelete by remember { mutableStateOf<Benchmark?>(null) }
    var showFabMenu by remember { mutableStateOf(false) }
    var showPasteImport by rememberSaveable { mutableStateOf(false) }
    var isSelectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedDates by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val existingDates = remember(entries) {
        entries.map { it.benchmark.date }.toSet()
    }

    // 선택 모드에 들어가면 최신 날짜(맨 앞 행)부터 볼 수 있도록 목록 맨 위로 이동한다.
    LaunchedEffect(isSelectionMode) {
        if (isSelectionMode) listState.animateScrollToItem(0)
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedDates = emptySet()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is BenchmarkUiState.Loading -> {
                LoadingIndicator(modifier = Modifier.align(Alignment.Center))
            }
            is BenchmarkUiState.Error -> {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
            }
            is BenchmarkUiState.Success -> {
                if (entries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.benchmark_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        BenchmarkHeaderRow(
                            isSelectionMode = isSelectionMode,
                            allSelected = selectedDates.size == entries.size,
                            onToggleSelectAll = {
                                selectedDates = if (selectedDates.size == entries.size) {
                                    emptySet()
                                } else {
                                    entries.map { it.benchmark.date }.toSet()
                                }
                            },
                        )
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .padding(bottom = 80.dp),
                            state = listState,
                        ) {
                            items(entries, key = { it.benchmark.date }) { entry ->
                                BenchmarkRow(
                                    entry = entry,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = entry.benchmark.date in selectedDates,
                                    onToggleSelected = {
                                        selectedDates = if (entry.benchmark.date in selectedDates) {
                                            selectedDates - entry.benchmark.date
                                        } else {
                                            selectedDates + entry.benchmark.date
                                        }
                                    },
                                    onEditRequest = { formTarget = BenchmarkFormTarget.Edit(entry.benchmark) },
                                    onDeleteRequest = { pendingDelete = entry.benchmark },
                                )
                            }
                        }
                    }
                }

                if (isSelectionMode) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { showDeleteAllConfirm = true },
                            enabled = entries.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.benchmark_delete_all)) }
                        Button(
                            onClick = {
                                viewModel.deleteBenchmarks(selectedDates.toList())
                                exitSelectionMode()
                            },
                            enabled = selectedDates.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.benchmark_delete_selected)) }
                        OutlinedButton(
                            onClick = { exitSelectionMode() },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.cancel)) }
                    }
                } else {
                    Box(modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box {
                                FloatingActionButton(onClick = { showFabMenu = true }) {
                                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.benchmark_add))
                                }
                                DropdownMenu(expanded = showFabMenu, onDismissRequest = { showFabMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.asset_add_individual)) },
                                        onClick = {
                                            showFabMenu = false
                                            formTarget = BenchmarkFormTarget.New
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
                            if (entries.isNotEmpty()) {
                                FloatingActionButton(onClick = { isSelectionMode = true }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.benchmark_bulk_delete))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    formTarget?.let { target ->
        BenchmarkFormDialog(
            initial = (target as? BenchmarkFormTarget.Edit)?.benchmark,
            existingDates = existingDates,
            onDismiss = { formTarget = null },
            onSave = { benchmark ->
                viewModel.saveBenchmark(benchmark)
                formTarget = null
            },
        )
    }

    if (showPasteImport) {
        BenchmarkPasteImportDialog(
            existingDates = existingDates,
            onDismiss = { showPasteImport = false },
            onParse = viewModel::parsePasteText,
            onImport = { benchmarks ->
                viewModel.importBenchmarks(benchmarks)
                showPasteImport = false
            },
        )
    }

    actionError?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.consumeActionError() },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.consumeActionError() }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }

    pendingDelete?.let { benchmark ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.benchmark_delete_confirm_title)) },
            text = { Text(stringResource(R.string.benchmark_delete_confirm_message, benchmark.date)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteBenchmark(benchmark.date)
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

    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text(stringResource(R.string.benchmark_delete_all_confirm_title)) },
            text = { Text(stringResource(R.string.benchmark_delete_all_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAllBenchmarks()
                    showDeleteAllConfirm = false
                    exitSelectionMode()
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun BenchmarkHeaderRow(
    isSelectionMode: Boolean,
    allSelected: Boolean,
    onToggleSelectAll: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = allSelected,
                    onCheckedChange = { onToggleSelectAll() },
                    modifier = Modifier.width(BENCHMARK_CHECKBOX_COLUMN_WIDTH),
                )
            }
            BenchmarkCell(stringResource(R.string.benchmark_field_date), BENCHMARK_DATE_COLUMN_WIDTH, bold = true)
            BenchmarkCell(stringResource(R.string.benchmark_field_principal), BENCHMARK_VALUE_COLUMN_WIDTH, bold = true)
            BenchmarkCell(stringResource(R.string.benchmark_field_additional_investment), BENCHMARK_VALUE_COLUMN_WIDTH, bold = true)
            BenchmarkCell(stringResource(R.string.benchmark_field_current_amount), BENCHMARK_VALUE_COLUMN_WIDTH, bold = true)
            BenchmarkCell(stringResource(R.string.benchmark_field_profit), BENCHMARK_VALUE_COLUMN_WIDTH, bold = true)
            BenchmarkCell(stringResource(R.string.benchmark_field_return_rate), BENCHMARK_PERCENT_COLUMN_WIDTH, bold = true)
            BenchmarkCell(stringResource(R.string.benchmark_field_change_rate), BENCHMARK_PERCENT_COLUMN_WIDTH, bold = true)
            BenchmarkCell(stringResource(R.string.benchmark_field_mdd), BENCHMARK_PERCENT_COLUMN_WIDTH, bold = true)
            BenchmarkCell(stringResource(R.string.benchmark_field_kospi), BENCHMARK_VALUE_COLUMN_WIDTH, bold = true)
            BenchmarkCell(stringResource(R.string.benchmark_field_kospi_return_rate), BENCHMARK_PERCENT_COLUMN_WIDTH, bold = true)
            BenchmarkCell(stringResource(R.string.benchmark_field_kospi_change_rate), BENCHMARK_PERCENT_COLUMN_WIDTH, bold = true)
            BenchmarkCell(stringResource(R.string.benchmark_field_kospi_mdd), BENCHMARK_PERCENT_COLUMN_WIDTH, bold = true)
            BenchmarkCell(stringResource(R.string.benchmark_field_snp500), BENCHMARK_VALUE_COLUMN_WIDTH, bold = true)
            BenchmarkCell(stringResource(R.string.benchmark_field_snp500_return_rate), BENCHMARK_PERCENT_COLUMN_WIDTH, bold = true)
            BenchmarkCell(stringResource(R.string.benchmark_field_snp500_change_rate), BENCHMARK_PERCENT_COLUMN_WIDTH, bold = true)
            BenchmarkCell(stringResource(R.string.benchmark_field_snp500_mdd), BENCHMARK_PERCENT_COLUMN_WIDTH, bold = true)
            BenchmarkCell(stringResource(R.string.benchmark_field_nasdaq), BENCHMARK_VALUE_COLUMN_WIDTH, bold = true)
            BenchmarkCell(stringResource(R.string.benchmark_field_nasdaq_return_rate), BENCHMARK_PERCENT_COLUMN_WIDTH, bold = true)
            BenchmarkCell(stringResource(R.string.benchmark_field_nasdaq_change_rate), BENCHMARK_PERCENT_COLUMN_WIDTH, bold = true)
            BenchmarkCell(stringResource(R.string.benchmark_field_nasdaq_mdd), BENCHMARK_PERCENT_COLUMN_WIDTH, bold = true)
            BenchmarkCell("", BENCHMARK_ACTION_COLUMN_WIDTH, bold = true)
        }
        HorizontalDivider()
    }
}

@Composable
private fun BenchmarkRow(
    entry: BenchmarkRowMetrics,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelected: () -> Unit,
    onEditRequest: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    val benchmark = entry.benchmark
    Column {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelected() },
                    modifier = Modifier.width(BENCHMARK_CHECKBOX_COLUMN_WIDTH),
                )
            }
            BenchmarkCell(benchmark.date, BENCHMARK_DATE_COLUMN_WIDTH)
            BenchmarkCell(entry.principal.toDisplayAmount(), BENCHMARK_VALUE_COLUMN_WIDTH)
            BenchmarkCell(benchmark.additionalInvestment.toDisplayAmount(), BENCHMARK_VALUE_COLUMN_WIDTH)
            BenchmarkCell(benchmark.currentAmount.toDisplayAmount(), BENCHMARK_VALUE_COLUMN_WIDTH)
            BenchmarkCell(entry.profit.toDisplayAmount(), BENCHMARK_VALUE_COLUMN_WIDTH, color = signColor(entry.profit))
            BenchmarkPercentCell(entry.returnRatePercent)
            BenchmarkPercentCell(entry.returnRateChangePercent)
            BenchmarkPercentCell(entry.assetMdd)
            BenchmarkCell(entry.kospi.value.toPlainString(), BENCHMARK_VALUE_COLUMN_WIDTH)
            BenchmarkPercentCell(entry.kospi.returnRatePercent)
            BenchmarkPercentCell(entry.kospi.changePercent)
            BenchmarkPercentCell(entry.kospi.mdd)
            BenchmarkCell(entry.snp500.value.toPlainString(), BENCHMARK_VALUE_COLUMN_WIDTH)
            BenchmarkPercentCell(entry.snp500.returnRatePercent)
            BenchmarkPercentCell(entry.snp500.changePercent)
            BenchmarkPercentCell(entry.snp500.mdd)
            BenchmarkCell(entry.nasdaq.value.toPlainString(), BENCHMARK_VALUE_COLUMN_WIDTH)
            BenchmarkPercentCell(entry.nasdaq.returnRatePercent)
            BenchmarkPercentCell(entry.nasdaq.changePercent)
            BenchmarkPercentCell(entry.nasdaq.mdd)
            Row(modifier = Modifier.width(BENCHMARK_ACTION_COLUMN_WIDTH)) {
                IconButton(onClick = onEditRequest, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit), modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDeleteRequest, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), modifier = Modifier.size(18.dp))
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun BenchmarkCell(text: String, width: Dp, bold: Boolean = false, color: Color = Color.Unspecified) {
    Text(
        text = text,
        modifier = Modifier.width(width).padding(end = 4.dp),
        style = if (bold) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

// 수익률/상승률/MDD처럼 null 가능한 퍼센트 값을 공통 스타일(음수는 빨강, 그 외는 강조색)로 표시한다.
@Composable
private fun BenchmarkPercentCell(value: BigDecimal?) {
    BenchmarkCell(
        text = value?.let { "$it%" } ?: "—",
        width = BENCHMARK_PERCENT_COLUMN_WIDTH,
        color = signColor(value),
    )
}

// 값의 부호에 따른 강조 색상: 음수는 error, 양수는 강조색, 0이거나 계산 불가(null)면 중립색.
// 수익금 컬러와 퍼센트 셀(BenchmarkPercentCell) 색상 로직이 서로 어긋나지 않도록 하나로 통합해 공유한다.
@Composable
private fun signColor(value: BigDecimal?): Color = when {
    value == null -> MaterialTheme.colorScheme.onSurfaceVariant
    value.signum() < 0 -> MaterialTheme.colorScheme.error
    value.signum() > 0 -> MaterialTheme.colorScheme.primary
    else -> Color.Unspecified
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BenchmarkFormDialog(
    initial: Benchmark?,
    existingDates: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Benchmark) -> Unit,
) {
    var date by rememberSaveable { mutableStateOf(initial?.date ?: DiaryViewModel.todayDate()) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var additionalInvestmentText by rememberSaveable { mutableStateOf(initial?.additionalInvestment?.toPlainString() ?: "") }
    var currentAmountText by rememberSaveable { mutableStateOf(initial?.currentAmount?.toPlainString() ?: "") }
    var kospiText by rememberSaveable { mutableStateOf(initial?.kospi?.toPlainString() ?: "") }
    var snp500Text by rememberSaveable { mutableStateOf(initial?.snp500?.toPlainString() ?: "") }
    var nasdaqText by rememberSaveable { mutableStateOf(initial?.nasdaq?.toPlainString() ?: "") }

    val additionalInvestment = additionalInvestmentText.trim().toBigDecimalOrNull()
    val currentAmount = currentAmountText.trim().toBigDecimalOrNull()
    val kospi = kospiText.trim().toBigDecimalOrNull()
    val snp500 = snp500Text.trim().toBigDecimalOrNull()
    val nasdaq = nasdaqText.trim().toBigDecimalOrNull()
    // 신규 추가인데 이미 존재하는 날짜를 고르면 저장 시 기존 문서를 조용히 덮어쓰게 되므로 막는다.
    val dateConflict = initial == null && date in existingDates
    val isValid = !dateConflict && additionalInvestment != null && currentAmount != null &&
        kospi != null && snp500 != null && nasdaq != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial != null) R.string.benchmark_edit_title else R.string.benchmark_add_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = date,
                    onValueChange = {},
                    readOnly = true,
                    enabled = initial == null,
                    isError = dateConflict,
                    supportingText = {
                        if (dateConflict) Text(stringResource(R.string.benchmark_date_conflict))
                    },
                    label = { Text(stringResource(R.string.benchmark_field_date)) },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }, enabled = initial == null) {
                            Icon(Icons.Default.DateRange, contentDescription = stringResource(R.string.asset_pick_date))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                BenchmarkNumberField(
                    additionalInvestmentText,
                    { additionalInvestmentText = it },
                    R.string.benchmark_field_additional_investment,
                    additionalInvestment != null,
                )
                BenchmarkNumberField(currentAmountText, { currentAmountText = it }, R.string.benchmark_field_current_amount, currentAmount != null)
                BenchmarkNumberField(kospiText, { kospiText = it }, R.string.benchmark_field_kospi, kospi != null)
                BenchmarkNumberField(snp500Text, { snp500Text = it }, R.string.benchmark_field_snp500, snp500 != null)
                BenchmarkNumberField(nasdaqText, { nasdaqText = it }, R.string.benchmark_field_nasdaq, nasdaq != null)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        Benchmark(
                            firestoreId = date,
                            date = date,
                            additionalInvestment = additionalInvestment!!,
                            currentAmount = currentAmount!!,
                            kospi = kospi!!,
                            snp500 = snp500!!,
                            nasdaq = nasdaq!!,
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

    if (showDatePicker) {
        IsoDatePickerDialog(
            initialDate = date,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                date = it
                showDatePicker = false
            },
        )
    }
}

@Composable
private fun BenchmarkNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes labelRes: Int,
    valid: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = value.isNotBlank() && !valid,
        supportingText = {
            if (value.isNotBlank() && !valid) Text(stringResource(R.string.asset_amount_invalid))
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun BenchmarkPasteImportDialog(
    existingDates: Set<String>,
    onDismiss: () -> Unit,
    onParse: (text: String) -> List<ParsedBenchmarkRow>,
    onImport: (List<Benchmark>) -> Unit,
) {
    PasteImportDialog(
        title = stringResource(R.string.benchmark_paste_import_title),
        description = stringResource(R.string.benchmark_paste_import_description),
        onDismiss = onDismiss,
        onParse = onParse,
        itemOf = { it.benchmark },
        errorOf = { it.error },
        rawLineOf = { it.rawLine },
        onImport = onImport,
    ) { benchmark ->
        val willOverwrite = benchmark.date in existingDates
        Text(
            text = "${benchmark.date} · ${benchmark.currentAmount.toDisplayAmount()}" +
                if (willOverwrite) " · ${stringResource(R.string.benchmark_paste_import_overwrite)}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = if (willOverwrite) MaterialTheme.colorScheme.tertiary else Color.Unspecified,
        )
    }
}
