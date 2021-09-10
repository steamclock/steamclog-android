package com.steamclock.steamclog

/**
 * DestinationLevels
 *
 * Created by shayla on 2020-01-23
 */
sealed class LogLevelPreset {

    object DebugVerbose: LogLevelPreset()
    object Debug: LogLevelPreset()
    object ReleaseAdvanced: LogLevelPreset()
    object Release: LogLevelPreset()

    val title: String
        get() = when(this) {
            is DebugVerbose -> "DebugVerbose"
            is Debug -> "Debug"
            is Release -> "Release"
            is ReleaseAdvanced -> "ReleaseAdvanced"
        }

    val console: LogLevel
        get() = when(this) {
            is DebugVerbose -> LogLevel.Verbose
            is Debug -> LogLevel.Debug
            is Release -> LogLevel.None
            is ReleaseAdvanced -> LogLevel.None
        }

    val disk: LogLevel
        get() = when(this) {
            is DebugVerbose -> LogLevel.Verbose
            is Debug -> LogLevel.Debug
            is Release -> LogLevel.Info
            is ReleaseAdvanced -> LogLevel.Debug
        }

    val remote: LogLevel
        get() = when(this) {
            is DebugVerbose -> LogLevel.None
            is Debug -> LogLevel.None
            is Release -> LogLevel.Info
            is ReleaseAdvanced -> LogLevel.Debug
        }

    override fun toString(): String {
        return "$title(console=$console, disk=$disk, remote=$remote)"
    }
}
