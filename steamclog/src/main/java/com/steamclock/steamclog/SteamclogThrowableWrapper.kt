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
    val extraData: String?): Throwable(originalMessage)