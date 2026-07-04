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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val appPreferences: AppPreferences,
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

    companion object {
        fun factory(appPreferences: AppPreferences): ViewModelProvider.Factory =
            viewModelFactory { initializer { SettingsViewModel(appPreferences) } }
    }
}
