package com.steamclock.steamclogsample

import android.app.Application
import com.steamclock.steamclog.clog
import java.io.IOException
import java.lang.Exception
import kotlin.reflect.KClass

/**
 * steamclog
 * Created by jake on 2020-03-27, 2:40 PM
 */
class App: Application() {
    override fun onCreate() {
        super.onCreate()
        val blocked: MutableSet<KClass<out Throwable>> = mutableSetOf(BlockedException1::class, BlockedException2::class)
        clog.initWith(BuildConfig.DEBUG, externalCacheDir, blocked)
    }
}

class BlockedException1(message: String): Exception(message)
class BlockedException2(message: String): Exception(message)
class AllowedException(message: String): Exception(message)