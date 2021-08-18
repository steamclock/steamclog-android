package com.steamclock.steamclogsample

import android.app.Application
import com.steamclock.steamclog.ThrowableFilter
import com.steamclock.steamclog.clog
import kotlin.reflect.KClass

/**
 * steamclog
 * Created by jake on 2020-03-27, 2:40 PM
 */
class App: Application() {
    override fun onCreate() {
        super.onCreate()
        clog.initWith(BuildConfig.DEBUG, externalCacheDir)
        clog.throwableFilter = ThrowableFilter { throwable ->
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

class BlockedException1(message: String): Exception(message)
class BlockedException2(message: String): Exception(message)
class AllowedException(message: String): Exception(message)