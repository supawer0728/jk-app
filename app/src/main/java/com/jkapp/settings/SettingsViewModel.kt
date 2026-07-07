package com.jkapp.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jkapp.auth.AuthRepository
import com.jkapp.auth.FirebaseAuthRepository
import com.jkapp.common.AppPreferences
import com.jkapp.common.DEFAULT_HAPTIC_INTENSITY
import com.jkapp.common.DarkModeSetting
import com.jkapp.user.UserPreference
import com.jkapp.user.UserRepository
import com.jkapp.user.UserRepositoryImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(
    private val appPreferences: AppPreferences,
    private val authRepository: AuthRepository = FirebaseAuthRepository(),
    private val userRepository: UserRepository = UserRepositoryImpl(),
) : ViewModel() {

    val darkModeSetting: StateFlow<DarkModeSetting> = appPreferences.darkModeSetting
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            DarkModeSetting.SYSTEM
        )

    fun setDarkModeSetting(setting: DarkModeSetting) {
        viewModelScope.launch {
            appPreferences.setDarkModeSetting(setting)
        }
    }

    val hapticIntensity: StateFlow<Int> = appPreferences.hapticIntensity
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            DEFAULT_HAPTIC_INTENSITY
        )

    fun setHapticIntensity(intensity: Int) {
        viewModelScope.launch {
            appPreferences.setHapticIntensity(intensity)
        }
    }

    private val currentUserId: StateFlow<String?> = authRepository.observeCurrentUserId()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // uid가 없으면(로그아웃 상태) 기본값을 보여준다. 로그인 후에는 users/{uid}.preference를 실시간 구독한다.
    val preference: StateFlow<UserPreference> = currentUserId
        .flatMapLatest { uid ->
            if (uid != null) userRepository.observePreference(uid) else flowOf(UserPreference())
        }
        .catch { e ->
            Log.w(TAG, "preference 구독 오류: ${e.localizedMessage}")
            emit(UserPreference())
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreference())

    fun setLanguage(language: String) {
        updatePreference { it.copy(language = language) }
    }

    fun setTimeZone(timeZone: String) {
        updatePreference { it.copy(timeZone = timeZone) }
    }

    private fun updatePreference(update: (UserPreference) -> UserPreference) {
        val uid = currentUserId.value ?: return
        viewModelScope.launch {
            userRepository.updatePreference(uid, update(preference.value))
        }
    }

    companion object {
        private const val TAG = "SettingsViewModel"

        fun factory(appPreferences: AppPreferences): ViewModelProvider.Factory =
            viewModelFactory { initializer { SettingsViewModel(appPreferences) } }
    }
}
