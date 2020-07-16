package com.steamclock.steamclog

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
     * todo: Should this be bundle name or something? Do we have access to that from inside the package?
     */
    var identifier: String = "steamclog",

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
    var requireRedacted: Boolean = false
) {
    constructor(writeFilePath: File) : this(writeFilePath, "steamclog")
}


