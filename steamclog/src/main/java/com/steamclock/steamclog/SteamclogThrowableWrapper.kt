package com.steamclock.steamclog

/**
 * SteamclogThrowableWrapper
 * Enables multiple types of data to be passed to our Destinations.
 *
 * Created by shayla on 2020-09-28
 */
data class SteamclogThrowableWrapper(
    val originalMessage: String,
    val originalThrowable: Throwable?,
    val attachLogFiles: Boolean?,
    val redactedObjectData: String?,
    val extraInfo: Map<String, Any>?): Throwable(originalMessage)
{
    companion object {
        fun from(throwable: Throwable?): SteamclogThrowableWrapper? {
            if (throwable == null) return null
            return throwable as? SteamclogThrowableWrapper
                ?: SteamclogThrowableWrapper(
                    throwable.message ?: throwable.toString(),
                    originalThrowable = throwable,
                    attachLogFiles = false, // Currently only attaching logs on User Reports.
                    redactedObjectData = null,
                    extraInfo = null
                )
        }
    }
}