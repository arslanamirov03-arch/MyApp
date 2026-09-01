package com.lexis.words.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
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
    // Card typography — each element is sized independently from the study screen.
    val wordSizeSp: Float = 36f,
    val translationSizeSp: Float = 26f,
    val answerSizeSp: Float = 19f,
    val imageHeightDp: Float = 150f,
    val fontChoice: Int = 0,
)

/** Wide ranges on purpose: the user asked to go from very small to very large. */
object TypeRanges {
    val WORD = 14f..96f
    val TRANSLATION = 12f..80f
    val ANSWER = 12f..48f
    val IMAGE = 60f..460f
    val FONT_NAMES = listOf("Nunito", "Системный", "С засечками", "Моноширинный")
}

class SettingsStore(private val context: Context) {
    private object Keys {
        val IMAGES = booleanPreferencesKey("images_enabled")
        val SOUND = booleanPreferencesKey("sound_enabled")
        val VIBRATION = booleanPreferencesKey("vibration_enabled")
        val BATCH = intPreferencesKey("batch_size")
        val LIMIT = intPreferencesKey("word_limit")
        val LAST_BACKUP = longPreferencesKey("last_backup_at")
        val WORD_SIZE = floatPreferencesKey("word_size_sp")
        val TRANSLATION_SIZE = floatPreferencesKey("translation_size_sp")
        val ANSWER_SIZE = floatPreferencesKey("answer_size_sp")
        val IMAGE_HEIGHT = floatPreferencesKey("image_height_dp")
        val FONT_CHOICE = intPreferencesKey("font_choice")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            imagesEnabled = p[Keys.IMAGES] ?: true,
            soundEnabled = p[Keys.SOUND] ?: true,
            vibrationEnabled = p[Keys.VIBRATION] ?: false,
            batchSize = p[Keys.BATCH] ?: 10,
            wordLimitPerList = p[Keys.LIMIT] ?: 500,
            lastBackupAt = p[Keys.LAST_BACKUP],
            wordSizeSp = p[Keys.WORD_SIZE] ?: 36f,
            translationSizeSp = p[Keys.TRANSLATION_SIZE] ?: 26f,
            answerSizeSp = p[Keys.ANSWER_SIZE] ?: 19f,
            imageHeightDp = p[Keys.IMAGE_HEIGHT] ?: 150f,
            fontChoice = p[Keys.FONT_CHOICE] ?: 0,
        )
    }

    suspend fun setImagesEnabled(v: Boolean) = context.dataStore.edit { it[Keys.IMAGES] = v }
    suspend fun setSoundEnabled(v: Boolean) = context.dataStore.edit { it[Keys.SOUND] = v }
    suspend fun setVibrationEnabled(v: Boolean) = context.dataStore.edit { it[Keys.VIBRATION] = v }
    suspend fun setBatchSize(v: Int) = context.dataStore.edit { it[Keys.BATCH] = v.coerceIn(1, 2000) }
    suspend fun setLastBackupAt(v: Long) = context.dataStore.edit { it[Keys.LAST_BACKUP] = v }

    suspend fun setWordSize(v: Float) = context.dataStore.edit { it[Keys.WORD_SIZE] = v.coerceIn(TypeRanges.WORD) }
    suspend fun setTranslationSize(v: Float) = context.dataStore.edit { it[Keys.TRANSLATION_SIZE] = v.coerceIn(TypeRanges.TRANSLATION) }
    suspend fun setAnswerSize(v: Float) = context.dataStore.edit { it[Keys.ANSWER_SIZE] = v.coerceIn(TypeRanges.ANSWER) }
    suspend fun setImageHeight(v: Float) = context.dataStore.edit { it[Keys.IMAGE_HEIGHT] = v.coerceIn(TypeRanges.IMAGE) }
    suspend fun setFontChoice(v: Int) = context.dataStore.edit { it[Keys.FONT_CHOICE] = v.coerceIn(0, TypeRanges.FONT_NAMES.lastIndex) }

    suspend fun resetTypography() = context.dataStore.edit {
        it.remove(Keys.WORD_SIZE); it.remove(Keys.TRANSLATION_SIZE)
        it.remove(Keys.ANSWER_SIZE); it.remove(Keys.IMAGE_HEIGHT); it.remove(Keys.FONT_CHOICE)
    }

    suspend fun currentOnce(): AppSettings = settings.first()
}
