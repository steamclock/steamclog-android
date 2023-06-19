package com.steamclock.steamclog

import java.io.File

/**
 * Config
 *
 * Created by shayla on 2020-01-23
 */
data class Config(

    /**
     * Required; BuildConfig is tied to the module (ie. the Steamclog library module), so we cannot use it to determine
     * a default logLevel as this will always be set to false when the library is being imported via JitPack.
     * As such this info must be given to us by the application.
     */
     val isDebug: Boolean,

    /**
     * Required; Location where the app wishes to store any log files generated (ex. externalCacheDir)
     * Required on creation and will not change.
     */
    val fileWritePath: File?,

    /**
     *  Optional; Determines how long generated log files are kept for.
     */
    var keepLogsForDays: Int = 3,

    /**
     *  Optional; Configuration for auto-rotating file behaviour
     */
    var autoRotateConfig: AutoRotateConfig = AutoRotateConfig(),

    /**
     * Optional; Indicates if objects being logged must implement the redacted interface.
     */
    var requireRedacted: Boolean = false,

    /**
     * Optional; If set the FilterOut interface will be called to determine if the Throwable
     * being logged should be sent to the remote destination, or be filtered out.
     * By default no Throwables will be filtered.
     */
    var filtering: FilterOut = FilterOut { false },

    /**
     * Optional; Destination logging levels. In most cases we should use the default values, but
     * could be changed at runtime to allow for more detailed reporting.
     */
    var logLevel: LogLevelPreset = if (isDebug) LogLevelPreset.Debug else LogLevelPreset.Release,

    /**
     * Attach detailed logs from disk (if available) to all user reports. Default is false.
     */
    var detailedLogsOnUserReports: Boolean = false,

    /**
     * Set a callback to collect additional app specific properties to associate with an error,
     * called any time a error/fatal/user report is logged. The `purpose` parameter indicates
     * what sort of error this is, particularly to allow the callee to associate more personal
     * data with user reports (where privacy issues are less of a concern). Note: unlike the
     * extra info passed into individual logging functions, this info is not redacted in any
     * way even if requireRedacted is set, the callback must handle and privacy preservation or redaction
     */
    var extraInfo: (ExtraInfoPurpose) -> Map<String, Any>
) {
    override fun toString(): String {
        return "Config(" +
                "\n  logLevel = $logLevel," +
                "\n  fileWritePath = $fileWritePath," +
                "\n  keepLogsForDays = $keepLogsForDays," +
                "\n  detailedLogsOnUserReports = $detailedLogsOnUserReports," +
                "\n  requireRedacted = $requireRedacted)"

    }
}


