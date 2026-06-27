package com.example.jkapp.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jkapp.auth.AuthViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException

@Composable
fun LoginScreen(viewModel: AuthViewModel) {
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val account = GoogleSignIn
                    .getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                val idToken = account.idToken
                if (idToken != null) {
                    viewModel.firebaseAuthWithGoogle(idToken) { success ->
                        if (!success) errorMessage = "로그인에 실패했습니다. 다시 시도해 주세요."
                    }
                } else {
                    errorMessage = "인증 토큰을 가져오지 못했습니다."
                }
            } catch (e: ApiException) {
                if (e.statusCode != GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                    errorMessage = "Google 로그인 오류 (${e.statusCode})"
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "환영합니다",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(onClick = { launcher.launch(viewModel.signInIntent) }) {
            Text("Google로 로그인")
        }
        errorMessage?.let { msg ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
