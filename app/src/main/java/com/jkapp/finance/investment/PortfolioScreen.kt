package com.jkapp.finance.investment

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jkapp.common.LoadingIndicator
import com.jkapp.finance.toDisplayAmount
import java.math.BigDecimal

// 파이 차트에 사용할 색상 팔레트 (최대 10개 그룹 대응)
private val PIE_COLORS = listOf(
    Color(0xFF4E79A7), Color(0xFFF28E2B), Color(0xFFE15759), Color(0xFF76B7B2),
    Color(0xFF59A14F), Color(0xFFEDC948), Color(0xFFB07AA1), Color(0xFFFF9DA7),
    Color(0xFF9C755F), Color(0xFFBAB0AC),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    viewModel: PortfolioViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedPortfolio by viewModel.selectedPortfolio.collectAsStateWithLifecycle()
    val pieSlices by viewModel.pieSlices.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    // 그룹 조건 입력 칩 선택지 — 최신 날짜 전체 명의 데이터에서 도출.
    val accountOptions by viewModel.accountOptions.collectAsStateWithLifecycle()
    val categoryOptions by viewModel.categoryOptions.collectAsStateWithLifecycle()
    val stockNameOptions by viewModel.stockNameOptions.collectAsStateWithLifecycle()

    var showPortfolioForm by remember { mutableStateOf<Portfolio?>(null) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("포트폴리오") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    selectedPortfolio?.let { portfolio ->
                        IconButton(onClick = { showPortfolioForm = portfolio }) {
                            Icon(Icons.Default.Edit, contentDescription = "포트폴리오 수정")
                        }
                        portfolio.firestoreId?.let { id ->
                            IconButton(onClick = { pendingDeleteId = id }) {
                                Icon(Icons.Default.Delete, contentDescription = "포트폴리오 삭제")
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showPortfolioForm = Portfolio(name = "") }) {
                Icon(Icons.Default.Add, contentDescription = "포트폴리오 추가")
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (val state = uiState) {
                is PortfolioUiState.Loading -> LoadingIndicator(modifier = Modifier.align(Alignment.Center))
                is PortfolioUiState.Error -> Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
                is PortfolioUiState.Success -> {
                    if (state.portfolios.isEmpty()) {
                        Text(
                            text = "포트폴리오가 없습니다. + 버튼으로 추가하세요.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // 포트폴리오 탭
                            ScrollableTabRow(
                                selectedTabIndex = state.portfolios.indexOfFirst {
                                    it.firestoreId == selectedPortfolio?.firestoreId
                                }.coerceAtLeast(0),
                                edgePadding = 0.dp,
                            ) {
                                state.portfolios.forEach { portfolio ->
                                    Tab(
                                        selected = portfolio.firestoreId == selectedPortfolio?.firestoreId,
                                        onClick = { portfolio.firestoreId?.let { viewModel.selectPortfolio(it) } },
                                        text = {
                                            Text(
                                                portfolio.name,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                    )
                                }
                            }

                            selectedPortfolio?.let { portfolio ->
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    item {
                                        PortfolioPieChart(
                                            slices = pieSlices,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                    item {
                                        PortfolioGroupLegend(slices = pieSlices)
                                    }
                                    if (portfolio.groups.isEmpty()) {
                                        item {
                                            Text(
                                                text = "그룹이 없습니다. 포트폴리오를 수정해 그룹을 추가하세요.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    showPortfolioForm?.let { initial ->
        PortfolioFormDialog(
            initial = initial,
            accountOptions = accountOptions,
            categoryOptions = categoryOptions,
            stockNameOptions = stockNameOptions,
            onDismiss = { showPortfolioForm = null },
            onSave = { portfolio ->
                viewModel.savePortfolio(portfolio)
                showPortfolioForm = null
            },
        )
    }

    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("포트폴리오 삭제") },
            text = { Text("이 포트폴리오를 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePortfolio(id)
                    pendingDeleteId = null
                }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("취소") }
            },
        )
    }

    actionError?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.consumeActionError() },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.consumeActionError() }) {
                    Text("확인")
                }
            },
        )
    }
}

@Composable
private fun PortfolioPieChart(
    slices: List<PieSlice>,
    modifier: Modifier = Modifier,
) {
    if (slices.isEmpty() || slices.all { it.actualRatioPct == BigDecimal.ZERO }) {
        Box(
            modifier = modifier.height(220.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "분류된 종목이 없습니다",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val total = slices.sumOf { it.actualRatioPct }
    val sweepAngles = if (total > BigDecimal.ZERO) {
        slices.map { it.actualRatioPct.toFloat() / total.toFloat() * 360f }
    } else {
        slices.map { 360f / slices.size }
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // 실제 비율 파이 차트 (외부 링) + 목표 비율 파이 차트 (내부 링)
        Box(
            modifier = Modifier.size(220.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val outerRadius = size.minDimension / 2f
                val outerStroke = outerRadius * 0.38f
                val innerRadius = outerRadius * 0.55f
                val innerStroke = outerRadius * 0.28f
                val center = Offset(size.width / 2f, size.height / 2f)

                // 외부 링: 실제 비율
                var startAngle = -90f
                sweepAngles.forEachIndexed { index, sweep ->
                    val color = PIE_COLORS[index % PIE_COLORS.size]
                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        // 슬라이스 간 1도 간격. sweep이 1도 미만이면 음수 arc가 되지 않도록 0으로 clamp.
                        sweepAngle = (sweep - 1f).coerceAtLeast(0f),
                        useCenter = false,
                        topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                        size = Size(outerRadius * 2f, outerRadius * 2f),
                        style = Stroke(width = outerStroke),
                    )
                    startAngle += sweep
                }

                // 내부 링: 목표 비율
                val targetTotal = slices.sumOf { it.targetRatioPct }.toFloat()
                if (targetTotal > 0f) {
                    var targetStartAngle = -90f
                    slices.forEachIndexed { index, slice ->
                        val sweep = slice.targetRatioPct.toFloat() / targetTotal * 360f
                        val color = PIE_COLORS[index % PIE_COLORS.size].copy(alpha = 0.4f)
                        drawArc(
                            color = color,
                            startAngle = targetStartAngle,
                            sweepAngle = (sweep - 1f).coerceAtLeast(0f),
                            useCenter = false,
                            topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
                            size = Size(innerRadius * 2f, innerRadius * 2f),
                            style = Stroke(width = innerStroke),
                        )
                        targetStartAngle += sweep
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("실제", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("목표", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun PortfolioGroupLegend(slices: List<PieSlice>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 헤더
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("그룹", style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f))
                Text("평가금액", style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(96.dp), fontWeight = FontWeight.Normal)
                Text("실제%", style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(48.dp))
                Text("목표%", style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(48.dp))
            }
            HorizontalDivider()
            slices.forEachIndexed { index, slice ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 색상 인디케이터
                    Canvas(modifier = Modifier.size(10.dp)) {
                        drawCircle(color = PIE_COLORS[index % PIE_COLORS.size])
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = slice.groupName,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = slice.amount.toDisplayAmount(),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(96.dp),
                    )
                    Text(
                        text = "${slice.actualRatioPct}%",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(48.dp),
                    )
                    Text(
                        text = "${slice.targetRatioPct.toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(48.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PortfolioFormDialog(
    initial: Portfolio,
    accountOptions: List<String>,
    categoryOptions: List<String>,
    stockNameOptions: List<String>,
    onDismiss: () -> Unit,
    onSave: (Portfolio) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var groups by remember { mutableStateOf(initial.groups) }
    var showGroupForm by remember { mutableStateOf<Pair<Int, PortfolioGroup>?>(null) } // index(-1=신규), group

    val ratioError = validatePortfolioGroups(groups)
    val isValid = name.isNotBlank() && ratioError == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.firestoreId == null) "포트폴리오 추가" else "포트폴리오 수정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("포트폴리오 이름") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("그룹 목록", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = {
                        showGroupForm = -1 to PortfolioGroup(name = "")
                    }) { Text("+ 그룹 추가") }
                }
                if (groups.isEmpty()) {
                    Text(
                        "그룹이 없습니다",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    groups.forEachIndexed { idx, group ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(group.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "목표 ${group.targetRatio}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { showGroupForm = idx to group }) {
                                    Icon(Icons.Default.Edit, contentDescription = "그룹 수정")
                                }
                                IconButton(onClick = {
                                    groups = groups.toMutableList().also { it.removeAt(idx) }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "그룹 삭제",
                                        tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
                // 목표 비율 합계 오류 표시
                ratioError?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(initial.copy(name = name.trim(), groups = groups))
                },
                enabled = isValid,
            ) { Text("저장") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )

    showGroupForm?.let { (idx, group) ->
        PortfolioGroupFormDialog(
            initial = group,
            accountOptions = accountOptions,
            categoryOptions = categoryOptions,
            stockNameOptions = stockNameOptions,
            onDismiss = { showGroupForm = null },
            onSave = { savedGroup ->
                groups = if (idx < 0) {
                    groups + savedGroup
                } else {
                    groups.toMutableList().also { it[idx] = savedGroup }
                }
                showGroupForm = null
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PortfolioGroupFormDialog(
    initial: PortfolioGroup,
    accountOptions: List<String>,
    categoryOptions: List<String>,
    stockNameOptions: List<String>,
    onDismiss: () -> Unit,
    onSave: (PortfolioGroup) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial.name) }
    // 각 축은 선택된 값 Set으로 관리한다(InvestmentFilterModal과 동일 패턴). 빈 Set = 해당 축 필터 안 함.
    var selectedOwners by remember { mutableStateOf(initial.owners.toSet()) }
    var selectedAccounts by remember { mutableStateOf(initial.accounts.toSet()) }
    var selectedCategories by remember { mutableStateOf(initial.categories.orEmpty().toSet()) }
    var selectedStockNames by remember { mutableStateOf(initial.stockNames.orEmpty().toSet()) }
    var ratioText by rememberSaveable { mutableStateOf(initial.targetRatio.toString()) }

    val ratioValid = ratioText.toIntOrNull()?.let { it in 0..100 } ?: false
    val isValid = name.isNotBlank() && ratioValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.name.isEmpty()) "그룹 추가" else "그룹 수정") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("그룹 이름") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                // 소유주는 고정 집합. 미선택 = 전체(제한 없음).
                ChipSelectSection(
                    title = "소유주 (미선택=전체)",
                    options = INVESTMENT_OWNERS,
                    selected = selectedOwners,
                    onToggle = { v -> selectedOwners = selectedOwners.toggle(v) },
                )
                if (accountOptions.isNotEmpty()) {
                    ChipSelectSection(
                        title = "계좌 (미선택=전체)",
                        options = accountOptions,
                        selected = selectedAccounts,
                        onToggle = { v -> selectedAccounts = selectedAccounts.toggle(v) },
                    )
                }
                if (categoryOptions.isNotEmpty()) {
                    ChipSelectSection(
                        title = "카테고리 (미선택=전체)",
                        options = categoryOptions,
                        selected = selectedCategories,
                        onToggle = { v -> selectedCategories = selectedCategories.toggle(v) },
                    )
                }
                if (stockNameOptions.isNotEmpty()) {
                    ChipSelectSection(
                        title = "종목 (미선택=전체)",
                        options = stockNameOptions,
                        selected = selectedStockNames,
                        onToggle = { v -> selectedStockNames = selectedStockNames.toggle(v) },
                    )
                }
                OutlinedTextField(
                    value = ratioText,
                    onValueChange = { ratioText = it },
                    label = { Text("목표 비율 (%)") },
                    singleLine = true,
                    isError = !ratioValid && ratioText.isNotBlank(),
                    supportingText = {
                        if (!ratioValid && ratioText.isNotBlank()) Text("0~100 정수를 입력하세요")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            owners = selectedOwners.toList(),
                            accounts = selectedAccounts.toList(),
                            // 카테고리·종목명은 nullable 시맨틱 유지: 미선택 시 null(해당 축 필터 안 함).
                            categories = selectedCategories.toList().takeIf { it.isNotEmpty() },
                            stockNames = selectedStockNames.toList().takeIf { it.isNotEmpty() },
                            targetRatio = ratioText.toIntOrNull() ?: 0,
                        )
                    )
                },
                enabled = isValid,
            ) { Text("저장") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

private fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipSelectSection(
    title: String,
    options: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option in selected,
                    onClick = { onToggle(option) },
                    label = { Text(option) },
                )
            }
        }
    }
}
