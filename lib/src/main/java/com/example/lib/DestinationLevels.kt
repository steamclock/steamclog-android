package com.example.lib

/**
 * DestinationLevels
 *
 * Created by shayla on 2020-01-23
 */
sealed class DestinationLevels(var console: LogLevel, var file: LogLevel, var crashlytics: LogLevel) {
    class Custom(console: LogLevel, file: LogLevel, crashlytics: LogLevel): DestinationLevels(console, file, crashlytics)

    object Develop: DestinationLevels(console = LogLevel.None, file = LogLevel.None, crashlytics = LogLevel.None)
    object Debug: DestinationLevels(console = LogLevel.Verbose, file = LogLevel.Verbose, crashlytics = LogLevel.None)
    object Test: DestinationLevels(console = LogLevel.None, file = LogLevel.None, crashlytics = LogLevel.None)
    object Release: DestinationLevels(console = LogLevel.None, file = LogLevel.None, crashlytics = LogLevel.None)
    object Restrict3rdParty: DestinationLevels(console = LogLevel.None, file = LogLevel.None, crashlytics = LogLevel.None)

    override fun toString(): String {
        return "DestinationLevels(console=$console, file=$file, crashlytics=$crashlytics)"
    }
}
