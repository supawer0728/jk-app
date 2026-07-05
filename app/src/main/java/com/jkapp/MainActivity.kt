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
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.jkapp.auth.AuthViewModel
import com.jkapp.data.AppPreferences
import com.jkapp.data.DarkModeSetting
import com.jkapp.data.drive.DriveRepositoryImpl
import com.jkapp.haptic.HapticController
import com.jkapp.haptic.LocalHapticController
import com.jkapp.nav.DiaryDetailRoute
import com.jkapp.nav.DiaryFormRoute
import com.jkapp.nav.HomeRoute
import com.jkapp.nav.LoginRoute
import com.jkapp.nav.RecordTypeManagementRoute
import com.jkapp.nav.SettingsRoute
import com.jkapp.ui.DiaryDetailScreen
import com.jkapp.ui.RecordTypeManagementScreen
import com.jkapp.ui.DiaryFormScreen
import com.jkapp.ui.BenchmarkViewModel
import com.jkapp.ui.DailyAssetInvestmentViewModel
import com.jkapp.ui.DailyAssetViewModel
import com.jkapp.ui.DiaryViewModel
import com.jkapp.ui.LoginScreen
import com.jkapp.ui.MainScreen
import com.jkapp.ui.SettingsScreen
import com.jkapp.ui.SettingsViewModel
import com.jkapp.ui.TabOrderViewModel
import com.jkapp.ui.theme.JkappTheme

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

                    val backStack = remember {
                        mutableStateListOf<Any>(
                            if (authViewModel.user.value != null) HomeRoute else LoginRoute
                        )
                    }

                    LaunchedEffect(user) {
                        val target: Any = if (user != null) HomeRoute else LoginRoute
                        if (backStack.lastOrNull() != target) {
                            backStack.clear()
                            backStack.add(target)
                        }
                    }

                    NavDisplay(
                        backStack = backStack,
                        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
                        entryProvider = entryProvider {
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
