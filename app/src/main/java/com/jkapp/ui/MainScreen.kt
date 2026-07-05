package com.jkapp.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.jkapp.auth.AuthViewModel
import com.jkapp.haptic.LocalHapticController
import com.jkapp.R
import java.math.BigDecimal

private const val TAB_PANEL_SWIPE_THRESHOLD_PX = 60f
private val TAB_BAR_COLOR_LIGHT = Color(0xFFDBD6EB)
private val TAB_BAR_COLOR_DARK = Color(0xFF30264F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: AuthViewModel,
    diaryViewModel: DiaryViewModel,
    dailyAssetViewModel: DailyAssetViewModel,
    investmentViewModel: DailyAssetInvestmentViewModel,
    benchmarkViewModel: BenchmarkViewModel,
    tabOrderViewModel: TabOrderViewModel,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToAdd: () -> Unit,
    onNavigateToRecordTypeManagement: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val currentUser = user ?: return
    val netWorth by dailyAssetViewModel.netWorth.collectAsStateWithLifecycle()
    val investmentAmount by benchmarkViewModel.latestCurrentAmount.collectAsStateWithLifecycle()
    val tabOrder by tabOrderViewModel.tabOrder.collectAsStateWithLifecycle()
    val isEditMode by tabOrderViewModel.isEditMode.collectAsStateWithLifecycle()

    LaunchedEffect(currentUser.uid) {
        tabOrderViewModel.loadTabOrder(currentUser.uid)
    }

    var selectedTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var showProfileMenu by remember { mutableStateOf(false) }
    var panelExpanded by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    val activity = LocalActivity.current
    val haptic = LocalHapticController.current

    BackHandler {
        when {
            panelExpanded -> {
                if (isEditMode) tabOrderViewModel.toggleEditMode()
                panelExpanded = false
            }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(selectedTab.labelRes)) },
                actions = {
                    Box {
                        IconButton(onClick = { showProfileMenu = true }) {
                            SubcomposeAsyncImage(
                                model = currentUser.photoUrl,
                                contentDescription = stringResource(R.string.profile),
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop,
                                error = {
                                    ProfileInitial(
                                        displayName = currentUser.displayName,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            )
                        }
                        DropdownMenu(
                            expanded = showProfileMenu,
                            onDismissRequest = { showProfileMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings)) },
                                onClick = {
                                    showProfileMenu = false
                                    onNavigateToSettings()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sign_out)) },
                                onClick = {
                                    showProfileMenu = false
                                    viewModel.signOut()
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            // 다크 모드 설정(SYSTEM/ON/OFF)에 따라 실제 적용된 색상 스킴의 밝기로 판단한다.
            val tabBarColor = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
                TAB_BAR_COLOR_DARK
            } else {
                TAB_BAR_COLOR_LIGHT
            }
            Column(
                modifier = Modifier.background(tabBarColor),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (selectedTab == MainTab.ASSET) {
                    NetWorthBanner(netWorth = netWorth, investmentAmount = investmentAmount)
                }
                var swipeAccumulator by remember { mutableFloatStateOf(0f) }
                TabBarHandle(
                    // swipeAccumulator는 onDragStart/End/Cancel에서 매번 리셋되므로 panelExpanded를
                    // key로 둘 필요가 없다. panelExpanded를 key로 쓰면 임계값을 넘어 그 값이 바뀌는
                    // 순간 이 pointerInput 자신이 재시작되어(코루틴이 취소되고 awaitFirstDown부터
                    // 다시 대기), 손가락을 떼지 않고 이어서 반대 방향으로 스와이프해도 인식되지 않는다.
                    modifier = Modifier.pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { swipeAccumulator = 0f },
                            onDragEnd = { swipeAccumulator = 0f },
                            onDragCancel = { swipeAccumulator = 0f },
                        ) { change, dragAmount ->
                            change.consume()
                            swipeAccumulator += dragAmount
                            if (swipeAccumulator < -TAB_PANEL_SWIPE_THRESHOLD_PX && !panelExpanded) {
                                panelExpanded = true
                            } else if (swipeAccumulator > TAB_PANEL_SWIPE_THRESHOLD_PX && panelExpanded) {
                                panelExpanded = false
                            }
                        }
                    }
                )
                AnimatedContent(
                    targetState = panelExpanded,
                    transitionSpec = {
                        if (targetState) {
                            slideInVertically(initialOffsetY = { it }) + fadeIn() togetherWith
                                slideOutVertically(targetOffsetY = { -it / 4 }) + fadeOut()
                        } else {
                            slideInVertically(initialOffsetY = { -it / 4 }) + fadeIn() togetherWith
                                slideOutVertically(targetOffsetY = { it }) + fadeOut()
                        }
                    },
                    label = "tabBottomPanel",
                ) { expanded ->
                    if (expanded) {
                        TabOrderPanel(
                            tabs = tabOrder,
                            isEditMode = isEditMode,
                            onToggleEditMode = {
                                haptic?.tick()
                                tabOrderViewModel.toggleEditMode()
                            },
                            onMove = { from, to -> tabOrderViewModel.moveTab(from, to) },
                            onSelectTab = { tab ->
                                haptic?.tick()
                                selectedTab = tab
                                panelExpanded = false
                            },
                        )
                    } else {
                        NavigationBar(containerColor = tabBarColor) {
                            tabOrder.take(MAIN_TAB_ROW_SIZE).forEach { tab ->
                                NavigationBarItem(
                                    selected = selectedTab == tab,
                                    onClick = {
                                        haptic?.tick()
                                        selectedTab = tab
                                    },
                                    icon = { Icon(tab.icon, contentDescription = null) },
                                    label = { Text(stringResource(tab.labelRes)) }
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                MainTab.HOME -> HomeTabScreen()
                MainTab.ASSET -> AssetScreen(
                    viewModel = dailyAssetViewModel,
                    investmentViewModel = investmentViewModel,
                    benchmarkViewModel = benchmarkViewModel,
                )
                MainTab.DIARY -> DiaryScreen(
                    viewModel = diaryViewModel,
                    onNavigateToDetail = onNavigateToDetail,
                    onNavigateToAdd = onNavigateToAdd,
                    onNavigateToRecordTypeManagement = onNavigateToRecordTypeManagement
                )
                MainTab.TODO -> TodoTabScreen()
                MainTab.CALENDAR -> CalendarTabScreen()
            }
        }
    }
}

private val TAB_BAR_HANDLE_WIDTH = 96.dp // 기본 32dp의 3배
private val TAB_BAR_HANDLE_HEIGHT = 4.dp
private val TAB_BAR_HANDLE_TOUCH_HEIGHT = TAB_BAR_HANDLE_HEIGHT * 3

// 하단바를 스와이프해 탭 목록 패널을 열 수 있다는 것을 알려주는 손잡이.
// 인식 범위(터치 영역)는 화면에 보이는 손잡이보다 세로로 3배 넓게 잡아 스와이프 제스처를 더 쉽게 인식한다.
@Composable
private fun TabBarHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(vertical = 8.dp)
            .size(width = TAB_BAR_HANDLE_WIDTH, height = TAB_BAR_HANDLE_TOUCH_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = TAB_BAR_HANDLE_WIDTH, height = TAB_BAR_HANDLE_HEIGHT)
                .background(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(2.dp),
                )
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

@Composable
private fun ProfileInitial(displayName: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape
        ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayName?.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
