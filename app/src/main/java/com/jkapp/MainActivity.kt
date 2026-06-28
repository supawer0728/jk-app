package com.jkapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.jkapp.auth.AuthViewModel
import com.jkapp.nav.DiaryDetailRoute
import com.jkapp.nav.DiaryFormRoute
import com.jkapp.nav.HomeRoute
import com.jkapp.nav.LoginRoute
import com.jkapp.ui.DiaryDetailScreen
import com.jkapp.ui.DiaryFormScreen
import com.jkapp.ui.DiaryViewModel
import com.jkapp.ui.LoginScreen
import com.jkapp.ui.MainScreen
import com.jkapp.ui.theme.JkappTheme

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val diaryViewModel: DiaryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JkappTheme {
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
                                onNavigateToDetail = { date ->
                                    backStack.add(DiaryDetailRoute(date))
                                },
                                onNavigateToAdd = {
                                    backStack.add(DiaryFormRoute())
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
                    }
                )
            }
        }
    }
}
