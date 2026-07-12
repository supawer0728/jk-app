package com.jkapp.finance.investment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 투자 종목 목록의 다중 선택 필터 모달 바텀시트.
 *
 * 소유주·계좌·카테고리·종목명 4개 축을 [FilterChip]으로 다중 선택한다.
 * 각 축에서 아무것도 선택하지 않으면 "전체(제한 없음)"로 처리된다.
 *
 * @param currentFilter 현재 적용된 필터 (초기값으로 사용)
 * @param ownerOptions 소유주 선택지 목록
 * @param accountOptions 계좌 선택지 목록
 * @param categoryOptions 카테고리 선택지 목록
 * @param stockNameOptions 종목명 선택지 목록
 * @param onApply 확인 버튼 클릭 시 새 필터 전달
 * @param onDismiss 취소/닫기 시 호출
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InvestmentFilterModal(
    currentFilter: InvestmentFilter,
    ownerOptions: List<String>,
    accountOptions: List<String>,
    categoryOptions: List<String>,
    stockNameOptions: List<String>,
    onApply: (InvestmentFilter) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    // 모달 내부의 임시 선택 상태 — 확인 누를 때만 onApply로 전달한다.
    var selectedOwners by remember { mutableStateOf(currentFilter.owners) }
    var selectedAccounts by remember { mutableStateOf(currentFilter.accounts) }
    var selectedCategories by remember { mutableStateOf(currentFilter.categories) }
    var selectedStockNames by remember { mutableStateOf(currentFilter.stockNames) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 헤더
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("필터", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = {
                    selectedOwners = emptySet()
                    selectedAccounts = emptySet()
                    selectedCategories = emptySet()
                    selectedStockNames = emptySet()
                }) { Text("초기화") }
            }

            HorizontalDivider()

            // 소유주 축
            if (ownerOptions.isNotEmpty()) {
                FilterSection(
                    title = "소유주",
                    options = ownerOptions,
                    selected = selectedOwners,
                    onToggle = { value ->
                        selectedOwners = if (value in selectedOwners) {
                            selectedOwners - value
                        } else {
                            selectedOwners + value
                        }
                    },
                )
            }

            // 계좌 축
            if (accountOptions.isNotEmpty()) {
                FilterSection(
                    title = "계좌",
                    options = accountOptions,
                    selected = selectedAccounts,
                    onToggle = { value ->
                        selectedAccounts = if (value in selectedAccounts) {
                            selectedAccounts - value
                        } else {
                            selectedAccounts + value
                        }
                    },
                )
            }

            // 카테고리 축
            if (categoryOptions.isNotEmpty()) {
                FilterSection(
                    title = "카테고리",
                    options = categoryOptions,
                    selected = selectedCategories,
                    onToggle = { value ->
                        selectedCategories = if (value in selectedCategories) {
                            selectedCategories - value
                        } else {
                            selectedCategories + value
                        }
                    },
                )
            }

            // 종목명 축
            if (stockNameOptions.isNotEmpty()) {
                FilterSection(
                    title = "종목",
                    options = stockNameOptions,
                    selected = selectedStockNames,
                    onToggle = { value ->
                        selectedStockNames = if (value in selectedStockNames) {
                            selectedStockNames - value
                        } else {
                            selectedStockNames + value
                        }
                    },
                )
            }

            HorizontalDivider()

            // 액션 버튼
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { Text("취소") }
                Button(
                    onClick = {
                        onApply(
                            InvestmentFilter(
                                owners = selectedOwners,
                                accounts = selectedAccounts,
                                categories = selectedCategories,
                                stockNames = selectedStockNames,
                            )
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("적용") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(
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
