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
     * Location where the app wishes to store any log files generated (ex. externalCacheDir)
     * Required on creation and will not change.
     */
    val fileWritePath: File? = null,

    /**
     * Destination logging levels
     */
    var logLevel: LogLevelPreset = LogLevelPreset.Develop,

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
    constructor(writeFilePath: File) : this(writeFilePath, firebaseAnalytics = null)
}


