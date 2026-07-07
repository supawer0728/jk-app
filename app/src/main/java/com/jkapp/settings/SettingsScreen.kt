package com.jkapp.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jkapp.R
import com.jkapp.common.DarkModeSetting
import com.jkapp.common.MAX_HAPTIC_INTENSITY

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val darkModeSetting by viewModel.darkModeSetting.collectAsStateWithLifecycle()
    val hapticIntensity by viewModel.hapticIntensity.collectAsStateWithLifecycle()
    val preference by viewModel.preference.collectAsStateWithLifecycle()
    // 슬라이더 위치는 로컬 상태로 즉시 반영하고, DataStore 저장은 손을 뗄 때(onValueChangeFinished)만
    // 한다. hapticIntensity StateFlow에 바로 바인딩하면 드래그 중 프레임마다 저장이 발생하고,
    // 저장이 비동기로 반영되는 동안 손가락과 엄지 위치가 어긋나 보인다.
    var sliderPosition by remember { mutableFloatStateOf(hapticIntensity.toFloat()) }
    LaunchedEffect(hapticIntensity) {
        sliderPosition = hapticIntensity.toFloat()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Text(
                text = stringResource(R.string.dark_mode),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            DarkModeOption.entries.forEach { option ->
                SettingsOptionRow(
                    label = stringResource(option.labelRes),
                    selected = darkModeSetting == option.setting,
                    onClick = { viewModel.setDarkModeSetting(option.setting) }
                )
            }
            Text(
                text = stringResource(R.string.haptic_intensity),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Slider(
                    value = sliderPosition,
                    onValueChange = { sliderPosition = it },
                    onValueChangeFinished = { viewModel.setHapticIntensity(sliderPosition.toInt()) },
                    valueRange = 0f..MAX_HAPTIC_INTENSITY.toFloat(),
                    steps = MAX_HAPTIC_INTENSITY - 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (sliderPosition.toInt() == 0) {
                        stringResource(R.string.haptic_intensity_off)
                    } else {
                        stringResource(R.string.haptic_intensity_value, sliderPosition.toInt())
                    },
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Text(
                text = stringResource(R.string.language),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            LanguageOption.entries.forEach { option ->
                SettingsOptionRow(
                    label = stringResource(option.labelRes),
                    selected = preference.language == option.code,
                    onClick = { viewModel.setLanguage(option.code) }
                )
            }
            Text(
                text = stringResource(R.string.time_zone),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            TimeZoneOption.entries.forEach { option ->
                SettingsOptionRow(
                    label = stringResource(option.labelRes),
                    selected = preference.timeZone == option.zoneId,
                    onClick = { viewModel.setTimeZone(option.zoneId) }
                )
            }
        }
    }
}

private enum class DarkModeOption(val setting: DarkModeSetting, val labelRes: Int) {
    SYSTEM(DarkModeSetting.SYSTEM, R.string.dark_mode_system),
    ON(DarkModeSetting.ON, R.string.dark_mode_on),
    OFF(DarkModeSetting.OFF, R.string.dark_mode_off),
}

// language는 "ko" 저장만 하고 실제 로케일 전환 로직은 구현하지 않는다(향후 다국어 지원용 필드, 이슈 #57).
private enum class LanguageOption(val code: String, val labelRes: Int) {
    KOREAN("ko", R.string.language_ko),
}

private enum class TimeZoneOption(val zoneId: String, val labelRes: Int) {
    ASIA_SEOUL("Asia/Seoul", R.string.time_zone_asia_seoul),
    UTC("UTC", R.string.time_zone_utc),
}

@Composable
private fun SettingsOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = label, modifier = Modifier.padding(start = 8.dp))
    }
}
