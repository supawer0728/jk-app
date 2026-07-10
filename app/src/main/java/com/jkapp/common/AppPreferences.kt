package com.jkapp.common

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

private val KEY_SHARED_ROOT_FOLDER_ID = stringPreferencesKey("shared_root_folder_id")
private const val DEFAULT_SHARED_ROOT_FOLDER_ID = "1btwHnlKZDUmANZciU0bxs0hJZpRkqgEF"

private val KEY_DARK_MODE_SETTING = stringPreferencesKey("dark_mode_setting")

enum class DarkModeSetting {
    SYSTEM, ON, OFF
}

private val KEY_HAPTIC_INTENSITY = intPreferencesKey("haptic_intensity")
const val DEFAULT_HAPTIC_INTENSITY = 5
const val MAX_HAPTIC_INTENSITY = 10

private val KEY_NOTIFICATION_MODE = stringPreferencesKey("notification_mode")
private val KEY_NOTIFICATION_SOUND = stringPreferencesKey("notification_sound")

// 리마인더 알림 방식(이슈 #71). OFF는 무음(소리·진동 없음)으로 알림만 조용히 표시한다.
enum class NotificationMode {
    SOUND, VIBRATE, SOUND_AND_VIBRATE, OFF;

    val hasSound: Boolean get() = this == SOUND || this == SOUND_AND_VIBRATE
    val hasVibration: Boolean get() = this == VIBRATE || this == SOUND_AND_VIBRATE
}

// 알림음 프리셋. 실제 사운드 URI는 notification 계층에서 시스템 기본음으로 해석한다(번들 오디오 없음).
enum class NotificationSound { DEFAULT, ALARM, RINGTONE }

// DataStore가 파일 손상 등으로 IOException을 방출해도 앱이 크래시되지 않도록 빈 설정으로 대체한다.
private val Context.safePreferencesData: Flow<Preferences>
    get() = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

class AppPreferences(private val context: Context) {

    val sharedRootFolderId: Flow<String?> = context.safePreferencesData
        .map { it[KEY_SHARED_ROOT_FOLDER_ID] ?: DEFAULT_SHARED_ROOT_FOLDER_ID }

    suspend fun setSharedRootFolderId(id: String?) {
        context.dataStore.edit { prefs ->
            if (id == null) prefs.remove(KEY_SHARED_ROOT_FOLDER_ID)
            else prefs[KEY_SHARED_ROOT_FOLDER_ID] = id
        }
    }

    val darkModeSetting: Flow<DarkModeSetting> = context.safePreferencesData
        .map { prefs ->
            prefs[KEY_DARK_MODE_SETTING]?.let { value ->
                runCatching { DarkModeSetting.valueOf(value) }.getOrNull()
            } ?: DarkModeSetting.SYSTEM
        }

    suspend fun setDarkModeSetting(setting: DarkModeSetting) {
        try {
            context.dataStore.edit { prefs ->
                prefs[KEY_DARK_MODE_SETTING] = setting.name
            }
        } catch (e: IOException) {
            // 읽기 경로(safePreferencesData)와 동일하게 쓰기 실패도 크래시 없이 무시한다.
        }
    }

    val hapticIntensity: Flow<Int> = context.safePreferencesData
        .map { prefs -> prefs[KEY_HAPTIC_INTENSITY]?.coerceIn(0, MAX_HAPTIC_INTENSITY) ?: DEFAULT_HAPTIC_INTENSITY }

    suspend fun setHapticIntensity(intensity: Int) {
        try {
            context.dataStore.edit { prefs ->
                prefs[KEY_HAPTIC_INTENSITY] = intensity.coerceIn(0, MAX_HAPTIC_INTENSITY)
            }
        } catch (e: IOException) {
            // 읽기 경로(safePreferencesData)와 동일하게 쓰기 실패도 크래시 없이 무시한다.
        }
    }

    val notificationMode: Flow<NotificationMode> = context.safePreferencesData
        .map { prefs ->
            prefs[KEY_NOTIFICATION_MODE]?.let { runCatching { NotificationMode.valueOf(it) }.getOrNull() }
                ?: NotificationMode.SOUND_AND_VIBRATE
        }

    suspend fun setNotificationMode(mode: NotificationMode) {
        try {
            context.dataStore.edit { prefs -> prefs[KEY_NOTIFICATION_MODE] = mode.name }
        } catch (e: IOException) {
            // 읽기 경로(safePreferencesData)와 동일하게 쓰기 실패도 크래시 없이 무시한다.
        }
    }

    val notificationSound: Flow<NotificationSound> = context.safePreferencesData
        .map { prefs ->
            prefs[KEY_NOTIFICATION_SOUND]?.let { runCatching { NotificationSound.valueOf(it) }.getOrNull() }
                ?: NotificationSound.DEFAULT
        }

    suspend fun setNotificationSound(sound: NotificationSound) {
        try {
            context.dataStore.edit { prefs -> prefs[KEY_NOTIFICATION_SOUND] = sound.name }
        } catch (e: IOException) {
            // 읽기 경로(safePreferencesData)와 동일하게 쓰기 실패도 크래시 없이 무시한다.
        }
    }
}
