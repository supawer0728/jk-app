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

internal fun resolveAuthRoute(isLoggedIn: Boolean): Any = if (isLoggedIn) HomeRoute else LoginRoute

internal fun resolveInitialRoute(
    minSplashDurationElapsed: Boolean,
    isAuthReady: Boolean,
    isLoggedIn: Boolean,
): Any = if (minSplashDurationElapsed && isAuthReady) resolveAuthRoute(isLoggedIn) else SplashRoute

internal fun navigateIfChanged(backStack: MutableList<Any>, target: Any) {
    if (backStack.lastOrNull() != target) {
        backStack.clear()
        backStack.add(target)
    }
}

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
                    val isAuthReady by authViewModel.isAuthReady.collectAsStateWithLifecycle()
                    var minSplashDurationElapsed by rememberSaveable { mutableStateOf(false) }

                    val backStack = remember {
                        mutableStateListOf<Any>(
                            resolveInitialRoute(
                                minSplashDurationElapsed = minSplashDurationElapsed,
                                isAuthReady = authViewModel.isAuthReady.value,
                                isLoggedIn = authViewModel.user.value != null,
                            )
                        )
                    }

                    LaunchedEffect(user, isAuthReady, minSplashDurationElapsed) {
                        if (isAuthReady && minSplashDurationElapsed) {
                            navigateIfChanged(backStack, resolveAuthRoute(user != null))
                        }
                    }

                    NavDisplay(
                        backStack = backStack,
                        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
                        entryProvider = entryProvider {
                            entry<SplashRoute> {
                                SplashScreen(onMinDurationElapsed = { minSplashDurationElapsed = true })
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
