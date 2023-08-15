package com.steamclock.steamclog

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
class SClogDataStore(private val context: Context) {

    companion object {
        private val Context.SClogDataStore: DataStore<Preferences> by preferencesDataStore(name = "SClogDataStore")
        private val hasLoggedFileCreationFailureKey = booleanPreferencesKey("has_logged_file_creation_failure")
    }

    val getHasLoggedFileCreationFailure: Flow<Boolean>
        get() = context.SClogDataStore.data.map {
            it[hasLoggedFileCreationFailureKey] ?: true
        }

    suspend fun setHasLoggedFileCreationFailure(value: Boolean) {
        context.SClogDataStore.edit { it[hasLoggedFileCreationFailureKey] = value }
    }
}