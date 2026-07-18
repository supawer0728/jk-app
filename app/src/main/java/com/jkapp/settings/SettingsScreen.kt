package com.jkapp.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jkapp.R
import com.jkapp.common.DarkModeSetting
import com.jkapp.common.MAX_HAPTIC_INTENSITY
import com.jkapp.common.NotificationMode
import com.jkapp.common.NotificationSound

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToTabOrderEdit: () -> Unit = {},
    onSignOut: () -> Unit = {},
) {
    val darkModeSetting by viewModel.darkModeSetting.collectAsStateWithLifecycle()
    val hapticIntensity by viewModel.hapticIntensity.collectAsStateWithLifecycle()
    val preference by viewModel.preference.collectAsStateWithLifecycle()
    val notificationMode by viewModel.notificationMode.collectAsStateWithLifecycle()
    val notificationSound by viewModel.notificationSound.collectAsStateWithLifecycle()
    // 슬라이더 위치는 로컬 상태로 즉시 반영하고, DataStore 저장은 손을 뗄 때(onValueChangeFinished)만
    // 한다. hapticIntensity StateFlow에 바로 바인딩하면 드래그 중 프레임마다 저장이 발생하고,
    // 저장이 비동기로 반영되는 동안 손가락과 엄지 위치가 어긋나 보인다.
    var sliderPosition by remember { mutableFloatStateOf(hapticIntensity.toFloat()) }
    LaunchedEffect(hapticIntensity) {
        sliderPosition = hapticIntensity.toFloat()
    }

    var showSignOutConfirm by remember { mutableStateOf(false) }
    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text(stringResource(R.string.sign_out_confirm_title)) },
            text = { Text(stringResource(R.string.sign_out_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutConfirm = false
                    onSignOut()
                }) {
                    Text(stringResource(R.string.sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 좌측 라벨을 고정폭으로 정렬하기 위해, 정렬 대상 라벨 문자열을 모아 최대 폭을 측정한다.
    // '탭 순서 변경'은 우측 화살표만 있고 좌측 라벨이 있어 정렬 대상에 포함하고, '로그아웃'은
    // 버튼 형태(우측 컨트롤 없음)라 정렬 대상에서 뺀다.
    val labelTexts = buildList {
        add(stringResource(R.string.dark_mode))
        add(stringResource(R.string.haptic_intensity))
        add(stringResource(R.string.language))
        add(stringResource(R.string.time_zone))
        add(stringResource(R.string.notification_mode))
        if (notificationMode.hasSound) add(stringResource(R.string.notification_sound))
        add(stringResource(R.string.tab_order_edit))
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
            AlignedLabelSettings(labelTexts = labelTexts) { labelWidth ->
                SettingsDropdownRow(
                    label = stringResource(R.string.dark_mode),
                    labelWidth = labelWidth,
                    options = DarkModeOption.entries,
                    selected = DarkModeOption.entries.firstOrNull { it.setting == darkModeSetting }
                        ?: DarkModeOption.SYSTEM,
                    optionLabel = { stringResource(it.labelRes) },
                    onSelect = { viewModel.setDarkModeSetting(it.setting) },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.haptic_intensity),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.width(labelWidth),
                    )
                    Slider(
                        value = sliderPosition,
                        onValueChange = { sliderPosition = it },
                        onValueChangeFinished = { viewModel.setHapticIntensity(sliderPosition.toInt()) },
                        valueRange = 0f..MAX_HAPTIC_INTENSITY.toFloat(),
                        steps = MAX_HAPTIC_INTENSITY - 1,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                    )
                    Text(
                        text = if (sliderPosition.toInt() == 0) {
                            stringResource(R.string.haptic_intensity_off)
                        } else {
                            stringResource(R.string.haptic_intensity_value, sliderPosition.toInt())
                        },
                    )
                }
                SettingsDropdownRow(
                    label = stringResource(R.string.language),
                    labelWidth = labelWidth,
                    options = LanguageOption.entries,
                    selected = LanguageOption.entries.firstOrNull { it.code == preference.language }
                        ?: LanguageOption.KOREAN,
                    optionLabel = { stringResource(it.labelRes) },
                    onSelect = { viewModel.setLanguage(it.code) },
                )
                SettingsDropdownRow(
                    label = stringResource(R.string.time_zone),
                    labelWidth = labelWidth,
                    options = TimeZoneOption.entries,
                    selected = TimeZoneOption.entries.firstOrNull { it.zoneId == preference.timeZone }
                        ?: TimeZoneOption.ASIA_SEOUL,
                    optionLabel = { stringResource(it.labelRes) },
                    onSelect = { viewModel.setTimeZone(it.zoneId) },
                )
                SettingsDropdownRow(
                    label = stringResource(R.string.notification_mode),
                    labelWidth = labelWidth,
                    options = NotificationModeOption.entries,
                    selected = NotificationModeOption.entries.firstOrNull { it.mode == notificationMode }
                        ?: NotificationModeOption.SOUND_AND_VIBRATE,
                    optionLabel = { stringResource(it.labelRes) },
                    onSelect = { viewModel.setNotificationMode(it.mode) },
                )
                // 알림음 선택은 소리를 내는 방식일 때만 의미가 있어, 그 경우에만 노출한다.
                if (notificationMode.hasSound) {
                    SettingsDropdownRow(
                        label = stringResource(R.string.notification_sound),
                        labelWidth = labelWidth,
                        options = NotificationSoundOption.entries,
                        selected = NotificationSoundOption.entries.firstOrNull { it.sound == notificationSound }
                            ?: NotificationSoundOption.DEFAULT,
                        optionLabel = { stringResource(it.labelRes) },
                        onSelect = { viewModel.setNotificationSound(it.sound) },
                    )
                }
                SettingsNavigationRow(
                    label = stringResource(R.string.tab_order_edit),
                    labelWidth = labelWidth,
                    onClick = onNavigateToTabOrderEdit,
                )
            }
            // 로그아웃은 버튼 형태라 좌측 라벨 정렬 대상에서 제외한다.
            SettingsActionRow(
                label = stringResource(R.string.sign_out),
                onClick = { showSignOutConfirm = true },
            )
        }
    }
}

// 좌측 라벨 고정폭 정렬 컨테이너. labelTexts를 SubcomposeLayout으로 measure해 최대 폭을 구한 뒤,
// 그 Dp 폭을 content에 넘겨 각 설정 행이 동일 폭의 좌측 라벨을 갖도록 한다.
@Composable
private fun AlignedLabelSettings(
    labelTexts: List<String>,
    content: @Composable (labelWidth: Dp) -> Unit,
) {
    val labelStyle = MaterialTheme.typography.titleMedium
    SubcomposeLayout { constraints ->
        val labelMaxWidthPx = subcompose("labels") {
            labelTexts.forEach { text ->
                Text(text = text, style = labelStyle, maxLines = 1)
            }
        }.maxOfOrNull { it.measure(constraints).width } ?: 0
        val labelWidthDp = labelMaxWidthPx.toDp()

        val contentPlaceables = subcompose("content") {
            Column { content(labelWidthDp) }
        }.map { it.measure(constraints) }

        val height = contentPlaceables.sumOf { it.height }
        val width = contentPlaceables.maxOfOrNull { it.width } ?: constraints.minWidth
        layout(width, height) {
            var y = 0
            contentPlaceables.forEach { placeable ->
                placeable.placeRelative(0, y)
                y += placeable.height
            }
        }
    }
}

@Composable
private fun SettingsNavigationRow(
    label: String,
    labelWidth: Dp,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(labelWidth),
        )
        Box(modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
        )
    }
}

// 로그아웃 등 좌측 라벨 정렬 대상이 아닌 액션 항목(우측 컨트롤 없음).
@Composable
private fun SettingsActionRow(
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SettingsDropdownRow(
    label: String,
    labelWidth: Dp,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(labelWidth),
        )
        Box(modifier = Modifier.weight(1f))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
        ) {
            OutlinedTextField(
                value = optionLabel(selected),
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .width(180.dp),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                    )
                }
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

private enum class NotificationModeOption(val mode: NotificationMode, val labelRes: Int) {
    SOUND(NotificationMode.SOUND, R.string.notification_mode_sound),
    VIBRATE(NotificationMode.VIBRATE, R.string.notification_mode_vibrate),
    SOUND_AND_VIBRATE(NotificationMode.SOUND_AND_VIBRATE, R.string.notification_mode_sound_and_vibrate),
    OFF(NotificationMode.OFF, R.string.notification_mode_off),
}

private enum class NotificationSoundOption(val sound: NotificationSound, val labelRes: Int) {
    DEFAULT(NotificationSound.DEFAULT, R.string.notification_sound_default),
    ALARM(NotificationSound.ALARM, R.string.notification_sound_alarm),
    RINGTONE(NotificationSound.RINGTONE, R.string.notification_sound_ringtone),
}
