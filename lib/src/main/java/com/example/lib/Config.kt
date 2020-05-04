package com.example.lib

import java.io.File

/**
 * Config
 *
 * Created by shayla on 2020-01-23
 */
data class Config(

    /**
     * todo: Should this be bundle name or something? Do we have access to that from inside the package?
     */
    var identifier: String = "steamclog",

    /**
     * Destination logging levels
     */
    val logLevel: LogLevelPreset = LogLevelPreset.Develop,

    /**
     * Location where the app wishes to store any log files generated
     * ex. externalCacheDir
     * todo: Can we set this for the app, or should we allow this as a config.
     */
    var fileWritePath: File? = null,

    /**
     *  Determines how long generated log files are kept for.
     */
    var keepLogsForDays: Int = 3,

    /**
     * Indicates if objects being logged must implement the redacted interface.
     */
    var requireRedacted: Boolean = false
)