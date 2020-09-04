package com.steamclock.steamclogsample

import android.app.Application
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.steamclock.steamclog.clog
import java.io.IOException

/**
 * steamclog
 * Created by jake on 2020-03-27, 2:40 PM
 */
class App: Application() {
    override fun onCreate() {
        super.onCreate()
        clog.initWith(fileWritePath = externalCacheDir, firebaseAnalytics = Firebase.analytics)
        clog.config.suppressRemoteThrowableNames.add(IOException::class.java.name)
    }
}