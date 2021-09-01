package com.steamclock.steamclog

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
     *  Configuration for auto-rotating file behaviour
     */
    var autoRotateConfig: AutoRotateConfig = AutoRotateConfig(),

    /**
     * Indicates if objects being logged must implement the redacted interface.
     */
    var requireRedacted: Boolean = false

) {
    override fun toString(): String {
        return "Config(" +
                "\n  logLevel = $logLevel," +
                "\n  fileWritePath = $fileWritePath," +
                "\n  keepLogsForDays = $keepLogsForDays," +
                "\n  requireRedacted = $requireRedacted)"

    }
}


