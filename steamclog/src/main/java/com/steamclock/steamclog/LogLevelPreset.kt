package com.steamclock.steamclog

/**
 * DestinationLevels
 *
 * Created by shayla on 2020-01-23
 */
sealed class LogLevelPreset {

    /// Disk: verbose, system: verbose, remote: none
    object Firehose: LogLevelPreset()

    /// Disk: none, system: debug, remote: none
    object Develop: LogLevelPreset()

    /// Disk: verbose, system: none, remote: warn
    object ReleaseAdvanced: LogLevelPreset()

    /// Disk: none, system: none, remote: warn
    object Release: LogLevelPreset()

    val title: String
        get() = when(this) {
            is Firehose -> "Firehose"
            is Develop -> "Develop"
            is ReleaseAdvanced -> "ReleaseAdvanced"
            is Release -> "Release"
        }

    val global: LogLevel
        get() = when(this) {
            is Firehose -> LogLevel.Info
            is Develop -> LogLevel.Info
            is ReleaseAdvanced -> LogLevel.Info
            is Release -> LogLevel.Info
        }

    val crashlytics: LogLevel
        get() = when(this) {
            is Firehose -> LogLevel.None
            is Develop -> LogLevel.None
            is ReleaseAdvanced -> LogLevel.Verbose
            is Release -> LogLevel.Info
        }

    val sentry: LogLevel
        get() = when(this) {
            is Firehose -> LogLevel.None
            is Develop -> LogLevel.None
            is ReleaseAdvanced -> LogLevel.Verbose
            is Release -> LogLevel.Info
        }

    val file: LogLevel
        get() = when(this) {
            is Firehose -> LogLevel.Verbose
            is Develop -> LogLevel.None
            is ReleaseAdvanced -> LogLevel.Verbose
            is Release -> LogLevel.None
        }

    val console: LogLevel
        get() = when(this) {
            is Firehose -> LogLevel.Verbose
            is Develop -> LogLevel.Debug
            is ReleaseAdvanced -> LogLevel.None
            is Release -> LogLevel.None
        }

    val analyticsEnabled: Boolean
        get() = when(this) {
            is Firehose -> false
            is Develop -> false
            is ReleaseAdvanced -> true
            is Release -> true
        }

    override fun toString(): String {
        return "$title(global=$global, console=$console, file=$file, crashlytics=$crashlytics, sentry=$sentry)"
    }
}
