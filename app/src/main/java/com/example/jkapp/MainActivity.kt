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
import com.example.jkapp.auth.AuthViewModel
import com.example.jkapp.nav.HomeRoute
import com.example.jkapp.nav.LoginRoute
import com.example.jkapp.ui.HomeScreen
import com.example.jkapp.ui.LoginScreen
import com.jkapp.ui.theme.JkappTheme

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()

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
                        entry<LoginRoute> { LoginScreen(viewModel = authViewModel) }
                        entry<HomeRoute> { HomeScreen(viewModel = authViewModel) }
                    }
                )
            }
        }
    }
}
