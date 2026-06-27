package com.example.jkapp.auth

import android.app.Application
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.ClearCredentialException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(app: Application) : AndroidViewModel(app) {

    private val auth = FirebaseAuth.getInstance()
    private val credentialManager = CredentialManager.create(app)

    private val _user = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val user: StateFlow<FirebaseUser?> = _user.asStateFlow()

    fun firebaseAuthWithGoogle(idToken: String, onResult: (Boolean) -> Unit) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                _user.value = auth.currentUser
                onResult(true)
            } else {
                onResult(false)
            }
        }
    }

    fun signOut() {
        auth.signOut()
        _user.value = null
        viewModelScope.launch {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (_: ClearCredentialException) {
                // sign-out already completed; credential state clear is best-effort
            }
        }
    }
}
