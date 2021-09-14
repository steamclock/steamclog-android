package com.steamclock.steamclog

/**
 * ThrowableBlocker functional interface (ie. should only contain a single method).
 * Allows the application to intercept Throwables when an error occurs and decide if the
 * Throwable should be blocked from being logged as an error by the crash reporting
 * destination.
 */
fun interface FilterOut {
    fun shouldBlock(throwable: Throwable): Boolean
}