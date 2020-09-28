package com.steamclock.steamclogsample

import android.app.Application
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.steamclock.steamclog.SteamcLog
import com.steamclock.steamclog.clog
import java.lang.Exception

/**
 * steamclog
 * Created by jake on 2020-03-27, 2:40 PM
 */
class App: Application(), SteamcLog.Suppressible {

    /**
     * Testing for suppressRemoteErrorFor)
     */
    class DoNotLogAsNonFatal: Exception()

    override fun onCreate() {
        super.onCreate()

        clog.initWith(fileWritePath = externalCacheDir, firebaseAnalytics = Firebase.analytics)
        clog.config.suppressible = this
    }

    /**
     * Allows us to suppress specific Throwables from being logged on our remote destinations as
     * non-fatal exceptions.
     */
    override fun suppressRemoteError(throwable: Throwable?): Boolean {
        return when(throwable) {
            is DoNotLogAsNonFatal -> true
            else -> false
        }
    }
}