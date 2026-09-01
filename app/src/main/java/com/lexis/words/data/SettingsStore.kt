package com.lexis.words.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "lexis_settings")

data class AppSettings(
    val imagesEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = false,
    val batchSize: Int = 10,
    val wordLimitPerList: Int = 500,
    val lastBackupAt: Long? = null,
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val IMAGES = booleanPreferencesKey("images_enabled")
        val SOUND = booleanPreferencesKey("sound_enabled")
        val VIBRATION = booleanPreferencesKey("vibration_enabled")
        val BATCH = intPreferencesKey("batch_size")
        val LIMIT = intPreferencesKey("word_limit")
        val LAST_BACKUP = longPreferencesKey("last_backup_at")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            imagesEnabled = p[Keys.IMAGES] ?: true,
            soundEnabled = p[Keys.SOUND] ?: true,
            vibrationEnabled = p[Keys.VIBRATION] ?: false,
            batchSize = p[Keys.BATCH] ?: 10,
            wordLimitPerList = p[Keys.LIMIT] ?: 500,
            lastBackupAt = p[Keys.LAST_BACKUP],
        )
    }

    suspend fun setImagesEnabled(v: Boolean) = context.dataStore.edit { it[Keys.IMAGES] = v }
    suspend fun setSoundEnabled(v: Boolean) = context.dataStore.edit { it[Keys.SOUND] = v }
    suspend fun setVibrationEnabled(v: Boolean) = context.dataStore.edit { it[Keys.VIBRATION] = v }
    suspend fun setBatchSize(v: Int) = context.dataStore.edit { it[Keys.BATCH] = v.coerceIn(1, 2000) }
    suspend fun setLastBackupAt(v: Long) = context.dataStore.edit { it[Keys.LAST_BACKUP] = v }

    suspend fun currentOnce(): AppSettings = settings.first()
}
