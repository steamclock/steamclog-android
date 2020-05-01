package com.example.lib

import android.util.Log

/**
 * LogLevel
 *
 * Created by shayla on 2020-01-23
 */
enum class LogLevel(val javaLevel: Int) {
    Verbose (Log.VERBOSE),
    Debug(Log.DEBUG),
    Info(Log.INFO),
    Warn(Log.WARN),
    Error(Log.ERROR),
    Fatal(Log.ASSERT),  // Timber.wtf uses Log.Assert level
    None(100); // Pick number not used by util.Log

    val emoji: String? by lazy {
        when(this) {
            Error -> "🚫"
            Fatal -> "🚫"
            Warn -> "⚠️"
            else -> null
        }
    }

    companion object {
        /**
         * Maps android.util.Log to Steamclog LogLevel
         */
        fun getLogLevel(javaPriority: Int): LogLevel? {
            return when (javaPriority) {
                Log.VERBOSE -> Verbose
                Log.DEBUG -> Debug
                Log.INFO -> Info
                Log.WARN -> Warn
                Log.ERROR -> Error
                Log.ASSERT -> Fatal
                else -> null

            }
        }
    }

}
