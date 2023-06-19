package com.steamclock.steamclog

import java.io.File

/**
 * SteamclogThrowableWrapper
 * Enables multiple types of data to be passed to our Destinations.
 *
 * Created by shayla on 2020-09-28
 */
data class SteamclogThrowableWrapper(
    val originalMessage: String,
    val originalThrowable: Throwable?,
    val logAttachmentUrl: File?,
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
                    logAttachmentUrl = null,
                    redactedObjectData = null,
                    extraInfo = null
                )
        }
    }
}