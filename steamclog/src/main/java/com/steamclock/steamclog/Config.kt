package com.steamclock.steamclog

import com.google.firebase.analytics.FirebaseAnalytics
import java.io.File

/**
 * Config
 *
 * Created by shayla on 2020-01-23
 */
data class Config(
    /**
     * BuildConfig is tied to the module (ie. the Steamclog library module), so we cannot use it to determine
     * a default logLevel as this will always be set to false when the library is being imported via JitPack.
     * As such this info must be given to us by the application.
     */
    val isDebug: Boolean = false,

    /**
     * Location where the app wishes to store any log files generated (ex. externalCacheDir)
     * Required on creation and will not change.
     */
    val fileWritePath: File? = null,

    /**
     * Destination logging levels
     */
    var logLevel: LogLevelPreset = if (isDebug) LogLevelPreset.Firehose else LogLevelPreset.Release,

    /**
     *  Determines how long generated log files are kept for.
     */
    var keepLogsForDays: Int = 3,

    /**
     * Indicates if objects being logged must implement the redacted interface.
     */
    var requireRedacted: Boolean = false,

    /**
     * Currently we cannot get the firebase instance from within the Steamclog library, so we
     * require the calling application to provide it if analytics are desired.
     */
    var firebaseAnalytics: FirebaseAnalytics? = null
) {
    constructor(isDebug: Boolean, writeFilePath: File) : this(isDebug, writeFilePath, firebaseAnalytics = null)

    override fun toString(): String {
        return "Config(" +
                "\n  logLevel = $logLevel," +
                "\n  fileWritePath = $fileWritePath," +
                "\n  firebaseAnalytics = ${firebaseAnalytics}," +
                "\n  keepLogsForDays = $keepLogsForDays," +
                "\n  requireRedacted = $requireRedacted)"
    }
}


