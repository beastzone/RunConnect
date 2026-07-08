package com.runconnect.app.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        private val KEY_USE_IMPERIAL = booleanPreferencesKey("use_imperial")
        private val KEY_MAPBOX_TOKEN = stringPreferencesKey("mapbox_token")
        private val KEY_MAX_HR = stringPreferencesKey("max_heart_rate")
        private val KEY_LAST_SYNC_TIME = longPreferencesKey("last_sync_time")
        private val KEY_DATA_DAYS_BACK = intPreferencesKey("data_days_back")
        private val KEY_HC_CHANGES_TOKEN = stringPreferencesKey("hc_changes_token")
        private val KEY_BACKGROUND_SYNC_ENABLED = booleanPreferencesKey("background_sync_enabled")
        private val KEY_LAST_BG_SYNC_TIME = longPreferencesKey("last_background_sync_time")
        private val KEY_HISTORY_IMPORTED_THROUGH = longPreferencesKey("history_imported_through")
    }

    val useImperial: Flow<Boolean> = dataStore.data.map { it[KEY_USE_IMPERIAL] ?: false }
    val mapboxToken: Flow<String> = dataStore.data.map { it[KEY_MAPBOX_TOKEN] ?: "" }
    val maxHeartRate: Flow<Int> = dataStore.data.map { it[KEY_MAX_HR]?.toIntOrNull() ?: 190 }
    val lastSyncTime: Flow<Long?> = dataStore.data.map { it[KEY_LAST_SYNC_TIME] }
    val dataDaysBack: Flow<Int> = dataStore.data.map { it[KEY_DATA_DAYS_BACK] ?: 90 }
    val hcChangesToken: Flow<String?> = dataStore.data.map { it[KEY_HC_CHANGES_TOKEN] }
    val backgroundSyncEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_BACKGROUND_SYNC_ENABLED] ?: false }
    val lastBackgroundSyncTime: Flow<Long?> = dataStore.data.map { it[KEY_LAST_BG_SYNC_TIME] }
    val historyImportedThrough: Flow<Long?> = dataStore.data.map { it[KEY_HISTORY_IMPORTED_THROUGH] }

    suspend fun setUseImperial(value: Boolean) { dataStore.edit { it[KEY_USE_IMPERIAL] = value } }
    suspend fun setMapboxToken(token: String) { dataStore.edit { it[KEY_MAPBOX_TOKEN] = token } }
    suspend fun setMaxHeartRate(bpm: Int) { dataStore.edit { it[KEY_MAX_HR] = bpm.toString() } }
    suspend fun setLastSyncTime(epochSeconds: Long) { dataStore.edit { it[KEY_LAST_SYNC_TIME] = epochSeconds } }
    suspend fun setDataDaysBack(days: Int) { dataStore.edit { it[KEY_DATA_DAYS_BACK] = days } }
    suspend fun setHcChangesToken(token: String?) {
        dataStore.edit { if (token == null) it.remove(KEY_HC_CHANGES_TOKEN) else it[KEY_HC_CHANGES_TOKEN] = token }
    }
    suspend fun setBackgroundSyncEnabled(enabled: Boolean) { dataStore.edit { it[KEY_BACKGROUND_SYNC_ENABLED] = enabled } }
    suspend fun setLastBackgroundSyncTime(epochSeconds: Long) { dataStore.edit { it[KEY_LAST_BG_SYNC_TIME] = epochSeconds } }
    suspend fun setHistoryImportedThrough(epochSeconds: Long) { dataStore.edit { it[KEY_HISTORY_IMPORTED_THROUGH] = epochSeconds } }
}
