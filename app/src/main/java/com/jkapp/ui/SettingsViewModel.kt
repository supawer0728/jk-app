package com.jkapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jkapp.data.AppPreferences
import com.jkapp.data.DarkModeSetting
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class SettingsViewModel(
    private val appPreferences: AppPreferences,
) : ViewModel() {

    // 최초 프레임부터 저장된 테마를 반영하기 위해 동기로 초기값을 읽는다. 그렇지 않으면
    // DataStore 첫 방출 전까지 SYSTEM 기준으로 그려졌다가 실제 값으로 전환되는 깜빡임이 생긴다.
    val darkModeSetting: StateFlow<DarkModeSetting> = appPreferences.darkModeSetting
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            runBlocking { appPreferences.darkModeSetting.first() }
        )

    fun setDarkModeSetting(setting: DarkModeSetting) {
        viewModelScope.launch {
            appPreferences.setDarkModeSetting(setting)
        }
    }

    companion object {
        fun factory(appPreferences: AppPreferences): ViewModelProvider.Factory =
            viewModelFactory { initializer { SettingsViewModel(appPreferences) } }
    }
}
