package com.jkapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jkapp.auth.AuthViewModel
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
                val currentUser by authViewModel.user.collectAsStateWithLifecycle()
                val user = currentUser
                if (user != null) {
                    HomeScreen(user = user, viewModel = authViewModel)
                } else {
                    LoginScreen(viewModel = authViewModel)
                }
            }
        }
    }
}
