package com.jkapp.ui

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
import com.jkapp.data.DarkModeSetting
import com.jkapp.data.MAX_HAPTIC_INTENSITY

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val darkModeSetting by viewModel.darkModeSetting.collectAsStateWithLifecycle()
    val hapticIntensity by viewModel.hapticIntensity.collectAsStateWithLifecycle()
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
                DarkModeOptionRow(
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
        }
    }
}

private enum class DarkModeOption(val setting: DarkModeSetting, val labelRes: Int) {
    SYSTEM(DarkModeSetting.SYSTEM, R.string.dark_mode_system),
    ON(DarkModeSetting.ON, R.string.dark_mode_on),
    OFF(DarkModeSetting.OFF, R.string.dark_mode_off),
}

@Composable
private fun DarkModeOptionRow(
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
