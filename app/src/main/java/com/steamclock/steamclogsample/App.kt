package com.steamclock.steamclogsample

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.steamclock.steamclog.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * steamclog
 * Created by jake on 2020-03-27, 2:40 PM
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        clog.initWith(
            context = applicationContext,
            config = Config(
                isDebug = BuildConfig.DEBUG,
                fileWritePath = externalCacheDir,
                autoRotateConfig = AutoRotateConfig(10L), // Short rotate so we can more easily test
                filtering = App.appFiltering,
                detailedLogsOnUserReports = true
            )
        )
    }

    companion object {
        val appFiltering = FilterOut { throwable ->
            when (throwable) {
                is BlockedException1 -> {
                    true
                }
                is BlockedException2 -> {
                    true
                }
                else -> {
                    false
                }
            }
        }
    }
}

class BlockedException1(message: String) : Exception(message)
class BlockedException2(message: String) : Exception(message)
class AllowedException(message: String) : Exception(message)