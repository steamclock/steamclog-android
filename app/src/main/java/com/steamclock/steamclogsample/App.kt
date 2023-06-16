package com.steamclock.steamclogsample

import android.app.Application
import com.steamclock.steamclog.Config
import com.steamclock.steamclog.ExtraInfoPurpose
import com.steamclock.steamclog.FilterOut
import com.steamclock.steamclog.clog

/**
 * steamclog
 * Created by jake on 2020-03-27, 2:40 PM
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        clog.initWith(Config(
            isDebug = BuildConfig.DEBUG,
            fileWritePath = externalCacheDir,
            filtering = appFiltering,
            detailedLogsOnUserReports = true,
            extraInfo = { purpose ->
                when (purpose) {
                    ExtraInfoPurpose.Error -> {
                        mapOf("ExtraInfoPurpose" to "Error")
                    }
                    ExtraInfoPurpose.Fatal -> {
                        mapOf("ExtraInfoPurpose" to "Fatal")
                    }
                    ExtraInfoPurpose.UserReport -> {
                        mapOf("ExtraInfoPurpose" to "UserReport")
                    }
                }
            }
        ))
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