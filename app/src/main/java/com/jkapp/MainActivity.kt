package com.jkapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.jkapp.auth.AuthViewModel
import com.jkapp.common.AppPreferences
import com.jkapp.common.DarkModeSetting
import com.jkapp.common.LoginScreen
import com.jkapp.common.MainScreen
import com.jkapp.common.SplashScreen
import com.jkapp.common.TabOrderViewModel
import com.jkapp.common.theme.JkappTheme
import com.jkapp.diary.DiaryDetailScreen
import com.jkapp.diary.DiaryFormScreen
import com.jkapp.diary.DiaryViewModel
import com.jkapp.diary.RecordTypeManagementScreen
import com.jkapp.drive.DriveRepositoryImpl
import com.jkapp.finance.asset.DailyAssetViewModel
import com.jkapp.finance.benchmark.BenchmarkViewModel
import com.jkapp.finance.investment.DailyAssetInvestmentViewModel
import com.jkapp.haptic.HapticController
import com.jkapp.haptic.LocalHapticController
import com.jkapp.nav.DiaryDetailRoute
import com.jkapp.nav.DiaryFormRoute
import com.jkapp.nav.HomeRoute
import com.jkapp.nav.LoginRoute
import com.jkapp.nav.RecordTypeManagementRoute
import com.jkapp.nav.SettingsRoute
import com.jkapp.nav.SplashRoute
import com.jkapp.settings.SettingsScreen
import com.jkapp.settings.SettingsViewModel
import kotlinx.coroutines.delay

private const val SPLASH_DURATION_MS = 1_500L

// 화면 회전 등으로 Activity가 재생성되어도 splashStartTime은 rememberSaveable로 보존되므로,
// 이미 흘러간 시간만큼을 제외한 나머지 시간만 대기한다. 그렇지 않으면 회전을 반복할 때마다
// 스플래시 타이머가 매번 처음부터 다시 시작되어 노출 시간이 계속 늘어난다.
internal fun remainingSplashDelayMs(
    splashStartTime: Long,
    now: Long,
    durationMs: Long = SPLASH_DURATION_MS,
): Long = (durationMs - (now - splashStartTime)).coerceAtLeast(0L)

internal fun resolveAuthRoute(isLoggedIn: Boolean): Any = if (isLoggedIn) HomeRoute else LoginRoute

class MainActivity : ComponentActivity() {

    private val appPreferences by lazy { AppPreferences(this) }
    private val hapticController by lazy { HapticController(this) }
    private val authViewModel: AuthViewModel by viewModels()
    private val diaryViewModel: DiaryViewModel by viewModels { DiaryViewModel.factory(DriveRepositoryImpl(this), appPreferences) }
    private val dailyAssetViewModel: DailyAssetViewModel by viewModels { DailyAssetViewModel.factory() }
    private val investmentViewModel: DailyAssetInvestmentViewModel by viewModels { DailyAssetInvestmentViewModel.factory() }
    private val benchmarkViewModel: BenchmarkViewModel by viewModels { BenchmarkViewModel.factory() }
    private val settingsViewModel: SettingsViewModel by viewModels { SettingsViewModel.factory(appPreferences) }
    private val tabOrderViewModel: TabOrderViewModel by viewModels { TabOrderViewModel.factory() }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkModeSetting by settingsViewModel.darkModeSetting.collectAsStateWithLifecycle()
            val darkTheme = when (darkModeSetting) {
                DarkModeSetting.SYSTEM -> isSystemInDarkTheme()
                DarkModeSetting.ON -> true
                DarkModeSetting.OFF -> false
            }
            val hapticIntensity by settingsViewModel.hapticIntensity.collectAsStateWithLifecycle()
            LaunchedEffect(hapticIntensity) {
                hapticController.intensity = hapticIntensity
            }

            JkappTheme(darkTheme = darkTheme) {
                CompositionLocalProvider(LocalHapticController provides hapticController) {
                    val user by authViewModel.user.collectAsStateWithLifecycle()

                    var splashFinished by rememberSaveable { mutableStateOf(false) }
                    val splashStartTime = rememberSaveable { System.currentTimeMillis() }
                    val backStack = remember {
                        mutableStateListOf<Any>(
                            if (splashFinished) resolveAuthRoute(authViewModel.user.value != null) else SplashRoute
                        )
                    }

                    LaunchedEffect(Unit) {
                        delay(remainingSplashDelayMs(splashStartTime, System.currentTimeMillis()))
                        splashFinished = true
                    }

                    LaunchedEffect(user, splashFinished) {
                        if (splashFinished) {
                            val target = resolveAuthRoute(user != null)
                            if (backStack.lastOrNull() != target) {
                                backStack.clear()
                                backStack.add(target)
                            }
                        }
                    }

                    NavDisplay(
                        backStack = backStack,
                        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
                        entryProvider = entryProvider {
                            entry<SplashRoute> {
                                SplashScreen()
                            }
                            entry<LoginRoute> {
                                LoginScreen(viewModel = authViewModel)
                            }
                            entry<HomeRoute> {
                                MainScreen(
                                    viewModel = authViewModel,
                                    diaryViewModel = diaryViewModel,
                                    dailyAssetViewModel = dailyAssetViewModel,
                                    investmentViewModel = investmentViewModel,
                                    benchmarkViewModel = benchmarkViewModel,
                                    tabOrderViewModel = tabOrderViewModel,
                                    onNavigateToDetail = { date ->
                                        backStack.add(DiaryDetailRoute(date))
                                    },
                                    onNavigateToAdd = {
                                        backStack.add(DiaryFormRoute())
                                    },
                                    onNavigateToRecordTypeManagement = {
                                        backStack.add(RecordTypeManagementRoute)
                                    },
                                    onNavigateToSettings = {
                                        backStack.add(SettingsRoute)
                                    }
                                )
                            }
                            entry<DiaryDetailRoute> { route ->
                                DiaryDetailScreen(
                                    viewModel = diaryViewModel,
                                    date = route.date,
                                    onBack = { backStack.removeLastOrNull() },
                                    onNavigateToEdit = { firestoreId ->
                                        backStack.add(DiaryFormRoute(firestoreId = firestoreId))
                                    }
                                )
                            }
                            entry<DiaryFormRoute> { route ->
                                DiaryFormScreen(
                                    viewModel = diaryViewModel,
                                    firestoreId = route.firestoreId,
                                    onBack = { backStack.removeLastOrNull() }
                                )
                            }
                            entry<RecordTypeManagementRoute> {
                                RecordTypeManagementScreen(
                                    viewModel = diaryViewModel,
                                    onBack = { backStack.removeLastOrNull() }
                                )
                            }
                            entry<SettingsRoute> {
                                SettingsScreen(
                                    viewModel = settingsViewModel,
                                    onBack = { backStack.removeLastOrNull() }
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}
