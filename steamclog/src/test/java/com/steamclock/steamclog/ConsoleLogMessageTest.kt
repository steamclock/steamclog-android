package com.steamclock.steamclog

import android.util.Log
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/**
 * Checks the message ConsoleDestination sends to Logcat (#148). Timber.DebugTree ignores the
 * throwable it is given, so the stack trace has to be part of this message.
 */
class ConsoleLogMessageTest {

    @Test
    fun clogError_withThrowable_appendsStackTrace() {
        val exception = IOException("timeout")
        val wrapper = SteamclogThrowableWrapper("Failed to load profile", exception, false, null, null)

        val message = generateConsoleLogMessage(Log.ERROR, wrapper, "Failed to load profile")

        assertEquals("🚫 Failed to load profile\n${exception.stackTraceToString()}", message)
    }

    @Test
    fun clogError_withoutThrowable_isMessageOnly() {
        val wrapper = SteamclogThrowableWrapper("Failed to load profile", null, false, null, null)

        val message = generateConsoleLogMessage(Log.ERROR, wrapper, "Failed to load profile")

        assertEquals("🚫 Failed to load profile", message)
    }

    @Test
    fun directTimberCall_printsStackTraceOnce() {
        // Timber passes the tree the caller's message with the stack trace already appended.
        val exception = IOException("timeout")
        val timberMessage = "Failed to load profile\n${exception.stackTraceToString()}"

        val message = generateConsoleLogMessage(Log.ERROR, exception, timberMessage)

        assertEquals("🚫 Failed to load profile\n${exception.stackTraceToString()}", message)
    }
}
