package com.example.steamclog

import android.app.Application
import com.example.lib.clog

/**
 * steamclog
 * Created by jake on 2020-03-27, 2:40 PM
 */
class App: Application() {
    override fun onCreate() {
        super.onCreate()
        clog.initialize(this)
    }
}