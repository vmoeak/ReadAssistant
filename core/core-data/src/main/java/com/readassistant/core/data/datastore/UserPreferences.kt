package com.readassistant.core.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        val THEME_TYPE = stringPreferencesKey("theme_type")
        val FONT_SIZE = floatPreferencesKey("font_size")
        val LINE_HEIGHT = floatPreferencesKey("line_height")
        val FONT_FAMILY = stringPreferencesKey("font_family")
        val CONTENT_MAX_WIDTH = intPreferencesKey("content_max_width")
        val CONTENT_PADDING = intPreferencesKey("content_padding")
        val SOURCE_LANGUAGE = stringPreferencesKey("source_language")
        val TARGET_LANGUAGE = stringPreferencesKey("target_language")
        val AUTO_SYNC_ENABLED = booleanPreferencesKey("auto_sync_enabled")
        val SYNC_INTERVAL_HOURS = intPreferencesKey("sync_interval_hours")
    }

    val themeType: Flow<String> = dataStore.data.map { it[THEME_TYPE] ?: "LIGHT" }
    val fontSize: Flow<Float> = dataStore.data.map { it[FONT_SIZE] ?: 16f }
    val lineHeight: Flow<Float> = dataStore.data.map { it[LINE_HEIGHT] ?: 1.75f }
    val fontFamily: Flow<String> = dataStore.data.map { it[FONT_FAMILY] ?: "default" }
    val contentMaxWidth: Flow<Int> = dataStore.data.map { it[CONTENT_MAX_WIDTH] ?: 720 }
    val contentPadding: Flow<Int> = dataStore.data.map { it[CONTENT_PADDING] ?: 24 }
    val sourceLanguage: Flow<String> = dataStore.data.map { it[SOURCE_LANGUAGE] ?: "en" }
    val targetLanguage: Flow<String> = dataStore.data.map { it[TARGET_LANGUAGE] ?: "zh" }
    val autoSyncEnabled: Flow<Boolean> = dataStore.data.map { it[AUTO_SYNC_ENABLED] ?: true }
    val syncIntervalHours: Flow<Int> = dataStore.data.map { it[SYNC_INTERVAL_HOURS] ?: 2 }

    suspend fun setThemeType(value: String) {
        dataStore.edit { it[THEME_TYPE] = value }
    }

    suspend fun setFontSize(value: Float) {
        dataStore.edit { it[FONT_SIZE] = value }
    }

    suspend fun setLineHeight(value: Float) {
        dataStore.edit { it[LINE_HEIGHT] = value }
    }

    suspend fun setFontFamily(value: String) {
        dataStore.edit { it[FONT_FAMILY] = value }
    }

    suspend fun setContentMaxWidth(value: Int) {
        dataStore.edit { it[CONTENT_MAX_WIDTH] = value }
    }

    suspend fun setContentPadding(value: Int) {
        dataStore.edit { it[CONTENT_PADDING] = value }
    }

    suspend fun setSourceLanguage(value: String) {
        dataStore.edit { it[SOURCE_LANGUAGE] = value }
    }

    suspend fun setTargetLanguage(value: String) {
        dataStore.edit { it[TARGET_LANGUAGE] = value }
    }

    suspend fun setAutoSyncEnabled(value: Boolean) {
        dataStore.edit { it[AUTO_SYNC_ENABLED] = value }
    }

    suspend fun setSyncIntervalHours(value: Int) {
        dataStore.edit { it[SYNC_INTERVAL_HOURS] = value }
    }
}
