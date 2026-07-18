package com.jkapp.common

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jkapp.R
import com.jkapp.auth.AuthViewModel
import com.jkapp.calendar.CalendarTabScreen
import com.jkapp.diary.DiaryScreen
import com.jkapp.diary.DiaryViewModel
import com.jkapp.finance.FinanceScreen
import com.jkapp.finance.asset.DailyAssetViewModel
import com.jkapp.finance.benchmark.BenchmarkViewModel
import com.jkapp.finance.toDisplayAmount
import com.jkapp.finance.investment.DailyAssetInvestmentViewModel
import com.jkapp.haptic.LocalHapticController
import com.jkapp.nav.BenchmarkChartType
import com.jkapp.todo.TodoScreen
import com.jkapp.todo.TodoViewModel
import java.math.BigDecimal

private val TAB_BAR_COLOR_LIGHT = Color(0xFFDBD6EB)
private val TAB_BAR_COLOR_DARK = Color(0xFF30264F)

// 한 화면에 항상 정확히 보이는 하단 탭 개수. 각 탭 항목 폭 = 화면 폭 / VISIBLE_TAB_COUNT로
// 고정해, 콘텐츠 탭 5개가 화면을 꽉 채우고 나머지(로그아웃·설정)는 가로 스크롤로 접근한다.
private const val VISIBLE_TAB_COUNT = 5
private val TAB_BAR_HEIGHT = 48.dp
private val TAB_DIVIDER_WIDTH = 1.dp

@Composable
fun MainScreen(
    viewModel: AuthViewModel,
    diaryViewModel: DiaryViewModel,
    dailyAssetViewModel: DailyAssetViewModel,
    investmentViewModel: DailyAssetInvestmentViewModel,
    benchmarkViewModel: BenchmarkViewModel,
    tabOrderViewModel: TabOrderViewModel,
    todoViewModel: TodoViewModel,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToAdd: () -> Unit,
    onNavigateToRecordTypeManagement: () -> Unit,
    onNavigateToTodoForm: (String?) -> Unit,
    onNavigateToTodoSubForm: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPortfolio: () -> Unit,
    onNavigateToChart: (chartType: BenchmarkChartType) -> Unit = {},
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val currentUser = user ?: return
    val netWorth by dailyAssetViewModel.netWorth.collectAsStateWithLifecycle()
    val investmentAmount by benchmarkViewModel.latestCurrentAmount.collectAsStateWithLifecycle()
    val tabOrder by tabOrderViewModel.tabOrder.collectAsStateWithLifecycle()

    LaunchedEffect(currentUser.uid) {
        tabOrderViewModel.loadTabOrder(currentUser.uid)
    }

    var selectedTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    val activity = LocalActivity.current
    val haptic = LocalHapticController.current

    BackHandler {
        when {
            selectedTab != MainTab.HOME -> selectedTab = MainTab.HOME
            else -> showExitConfirm = true
        }
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text(stringResource(R.string.exit_app_title)) },
            text = { Text(stringResource(R.string.exit_app_message)) },
            confirmButton = {
                TextButton(onClick = { activity?.finish() }) {
                    Text(stringResource(R.string.exit_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text(stringResource(R.string.sign_out_confirm_title)) },
            text = { Text(stringResource(R.string.sign_out_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutConfirm = false
                    viewModel.signOut()
                }) {
                    Text(stringResource(R.string.sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    val tabBarColor = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
        TAB_BAR_COLOR_DARK
    } else {
        TAB_BAR_COLOR_LIGHT
    }

    val bottomItems: List<BottomTabItem> =
        tabOrder.map { BottomTabItem.Content(it) } +
            listOf(BottomTabItem.Action.Logout, BottomTabItem.Action.Settings)

    Scaffold(
        bottomBar = {
            Column(
                modifier = Modifier.background(tabBarColor),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (selectedTab == MainTab.ASSET) {
                    NetWorthBanner(netWorth = netWorth, investmentAmount = investmentAmount)
                }
                // 각 탭 항목 폭을 화면 폭의 1/VISIBLE_TAB_COUNT로 고정해 항상 정확히 5개가
                // 한 화면에 보이게 하고, 나머지(로그아웃·설정)는 가로 스크롤로 접근한다.
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val tabWidth = maxWidth / VISIBLE_TAB_COUNT
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .background(tabBarColor),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        bottomItems.forEachIndexed { index, item ->
                            BottomTabCell(
                                width = tabWidth,
                                showDivider = index > 0,
                                label = when (item) {
                                    is BottomTabItem.Content -> stringResource(item.tab.labelRes)
                                    is BottomTabItem.Action.Logout -> stringResource(R.string.sign_out)
                                    is BottomTabItem.Action.Settings -> stringResource(R.string.settings)
                                },
                                selected = item is BottomTabItem.Content && selectedTab == item.tab,
                                onClick = {
                                    when (item) {
                                        is BottomTabItem.Content -> {
                                            haptic?.tick()
                                            selectedTab = item.tab
                                        }
                                        is BottomTabItem.Action.Logout -> showSignOutConfirm = true
                                        is BottomTabItem.Action.Settings -> onNavigateToSettings()
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                MainTab.HOME -> HomeTabScreen()
                MainTab.ASSET -> FinanceScreen(
                    viewModel = dailyAssetViewModel,
                    investmentViewModel = investmentViewModel,
                    benchmarkViewModel = benchmarkViewModel,
                    onNavigateToPortfolio = onNavigateToPortfolio,
                    onNavigateToChart = onNavigateToChart,
                )
                MainTab.DIARY -> DiaryScreen(
                    viewModel = diaryViewModel,
                    onNavigateToDetail = onNavigateToDetail,
                    onNavigateToAdd = onNavigateToAdd,
                    onNavigateToRecordTypeManagement = onNavigateToRecordTypeManagement
                )
                MainTab.TODO -> TodoScreen(
                    viewModel = todoViewModel,
                    onNavigateToForm = onNavigateToTodoForm,
                    onNavigateToSubForm = onNavigateToTodoSubForm,
                )
                MainTab.CALENDAR -> CalendarTabScreen()
            }
        }
    }
}

// 하단 탭 한 칸. 폭은 화면 폭/VISIBLE_TAB_COUNT로 고정된다. 구분선은 셀 폭을 잠식하지 않도록
// 셀 내부 왼쪽 경계에 겹쳐 그린다(항목 5개 폭의 합이 정확히 화면 폭이 되도록).
@Composable
private fun BottomTabCell(
    width: Dp,
    showDivider: Boolean,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(TAB_BAR_HEIGHT)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (showDivider) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(TAB_DIVIDER_WIDTH)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NetWorthBanner(netWorth: BigDecimal?, investmentAmount: BigDecimal?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BannerStat(labelRes = R.string.net_worth_label, value = netWorth)
        BannerStat(labelRes = R.string.investment_asset_label, value = investmentAmount)
    }
}

@Composable
private fun BannerStat(@StringRes labelRes: Int, value: BigDecimal?) {
    Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = value?.toDisplayAmount() ?: "-",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
}
