package com.jkapp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

private val KEY_SHARED_ROOT_FOLDER_ID = stringPreferencesKey("shared_root_folder_id")
private const val DEFAULT_SHARED_ROOT_FOLDER_ID = "1btwHnlKZDUmANZciU0bxs0hJZpRkqgEF"

class AppPreferences(private val context: Context) {

    val sharedRootFolderId: Flow<String?> = context.dataStore.data
        .map { it[KEY_SHARED_ROOT_FOLDER_ID] ?: DEFAULT_SHARED_ROOT_FOLDER_ID }

    suspend fun setSharedRootFolderId(id: String?) {
        context.dataStore.edit { prefs ->
            if (id == null) prefs.remove(KEY_SHARED_ROOT_FOLDER_ID)
            else prefs[KEY_SHARED_ROOT_FOLDER_ID] = id
        }
    }
}
