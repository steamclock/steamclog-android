package com.steamclock.steamclog

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * https://developer.android.com/topic/libraries/architecture/datastore
 */
class SClogDataStore(private val application: Application) {
    companion object {
        private val Context.SClogDataStore: DataStore<Preferences> by preferencesDataStore(name = "SClogDataStore")
        private val hasReportedFilepathErrorKey = booleanPreferencesKey("has_logged_file_creation_failure")
    }

    /**
     * hasReportedFilepathError indicates if Steamclog has reported a Sentry error regarding
     * it's inability to use the given filePath to store logs.
     */
    val getHasReportedFilepathError: Flow<Boolean>
        get() = application.SClogDataStore.data.map {
            it[hasReportedFilepathErrorKey] ?: false
        }
    suspend fun setHasReportedFilepathError(value: Boolean) {
        application.SClogDataStore.edit { it[hasReportedFilepathErrorKey] = value }
    }
}