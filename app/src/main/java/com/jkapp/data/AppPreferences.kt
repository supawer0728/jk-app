package com.jkapp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
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
}
