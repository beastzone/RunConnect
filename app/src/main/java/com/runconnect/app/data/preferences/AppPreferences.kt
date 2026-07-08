package com.runconnect.app.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
    }

    val useImperial: Flow<Boolean> = dataStore.data.map { it[KEY_USE_IMPERIAL] ?: false }

    val mapboxToken: Flow<String> = dataStore.data.map { it[KEY_MAPBOX_TOKEN] ?: "" }

    val maxHeartRate: Flow<Int> = dataStore.data.map {
        it[KEY_MAX_HR]?.toIntOrNull() ?: 190
    }

    suspend fun setUseImperial(value: Boolean) {
        dataStore.edit { it[KEY_USE_IMPERIAL] = value }
    }

    suspend fun setMapboxToken(token: String) {
        dataStore.edit { it[KEY_MAPBOX_TOKEN] = token }
    }

    suspend fun setMaxHeartRate(bpm: Int) {
        dataStore.edit { it[KEY_MAX_HR] = bpm.toString() }
    }
}
