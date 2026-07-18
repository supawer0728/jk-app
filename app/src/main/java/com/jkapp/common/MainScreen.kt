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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

// 양 끝 고정 셀(홈·설정 아이콘) 배경. 하단바 기본색(TAB_BAR_COLOR_*)보다 조금 더 짙게 두어
// 스크롤 영역과 시각적으로 구분한다.
private val PINNED_TAB_COLOR_LIGHT = Color(0xFFC7C0E0)
private val PINNED_TAB_COLOR_DARK = Color(0xFF241C3D)

private val TAB_BAR_HEIGHT = 48.dp
private val TAB_DIVIDER_WIDTH = 1.dp
private val PINNED_TAB_HORIZONTAL_PADDING = 16.dp
// 고정폭 스크롤 탭 셀 내부의 텍스트 좌우 여백. 셀 폭이 좁은 소형 화면에서도 라벨이 최대한
// 보이도록 작게 둔다(넘치면 말줄임 처리).
private val SCROLL_TAB_HORIZONTAL_PADDING = 4.dp

// 홈·설정 아이콘 사이(가운데 스크롤 영역)에 한 번에 보이는 탭 개수. 각 탭 폭 = 가운데 영역
// 폭 / VISIBLE_SCROLL_TAB_COUNT로 고정해 4개가 딱 보이고, 나머지(로그아웃)는 가로 스크롤로 접근한다.
private const val VISIBLE_SCROLL_TAB_COUNT = 4

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

    val darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val tabBarColor = if (darkTheme) TAB_BAR_COLOR_DARK else TAB_BAR_COLOR_LIGHT
    val pinnedTabColor = if (darkTheme) PINNED_TAB_COLOR_DARK else PINNED_TAB_COLOR_LIGHT

    // 가운데 가로 스크롤 영역에 노출할 항목: 홈을 제외한 콘텐츠 탭(순서 반영) + 로그아웃(액션).
    val scrollableTabs: List<BottomTabItem> =
        tabOrder.filterNot { it == MainTab.HOME }.map { BottomTabItem.Content(it) } +
            BottomTabItem.Action.Logout

    Scaffold(
        bottomBar = {
            Column(
                // 배경(tabBarColor)은 시스템 내비게이션 바 영역까지 칠하되, navigationBarsPadding으로
                // 실제 탭 콘텐츠는 시스템 내비 바(제스처/3버튼 바) 위로 올려 가려지지 않게 한다.
                modifier = Modifier
                    .background(tabBarColor)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (selectedTab == MainTab.ASSET) {
                    NetWorthBanner(netWorth = netWorth, investmentAmount = investmentAmount)
                }
                // 하단바 3구역: [홈 아이콘 고정] | [가운데 가로 스크롤 텍스트 탭] | [설정 아이콘 고정].
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(tabBarColor),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PinnedIconTab(
                        icon = Icons.Default.Home,
                        contentDescription = stringResource(R.string.tab_home),
                        selected = selectedTab == MainTab.HOME,
                        background = pinnedTabColor,
                        onClick = {
                            haptic?.tick()
                            selectedTab = MainTab.HOME
                        },
                    )
                    // 가운데 영역 폭을 VISIBLE_SCROLL_TAB_COUNT로 나눠 각 탭 폭을 고정한다.
                    // 홈·설정 아이콘 사이에 4개가 딱 보이고 나머지(로그아웃)는 가로 스크롤로 접근한다.
                    BoxWithConstraints(modifier = Modifier.weight(1f)) {
                        val scrollTabWidth = maxWidth / VISIBLE_SCROLL_TAB_COUNT
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            scrollableTabs.forEachIndexed { index, item ->
                                ScrollableTextTab(
                                    width = scrollTabWidth,
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
                    PinnedIconTab(
                        icon = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.settings),
                        selected = false,
                        background = pinnedTabColor,
                        onClick = onNavigateToSettings,
                    )
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

// 하단바 양 끝에 고정되는 아이콘 탭(홈·설정). 배경을 하단바 기본색보다 조금 짙게 칠해
// 가운데 스크롤 영역과 구분한다. 선택 상태가 있으면(홈) primary tint로 강조한다.
@Composable
private fun PinnedIconTab(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    background: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(TAB_BAR_HEIGHT)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = PINNED_TAB_HORIZONTAL_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

// 가운데 가로 스크롤 영역의 텍스트 탭 한 칸. 폭은 가운데 영역 폭/VISIBLE_SCROLL_TAB_COUNT로
// 고정된다. 구분선은 셀 폭을 잠식하지 않도록 셀 내부 왼쪽 경계에 겹쳐 그려(4개 폭 합이 정확히
// 가운데 영역 폭이 되도록) 앞선 항목과의 경계를 표시하고, 선택된 콘텐츠 탭은 Bold + primary로 강조한다.
@Composable
private fun ScrollableTextTab(
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = SCROLL_TAB_HORIZONTAL_PADDING),
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
