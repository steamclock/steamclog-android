package com.steamclock.steamclog

import android.util.Log
import io.sentry.SentryLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SentryEventLevelTest {

    @Test
    fun error_capturesErrorEvent() {
        assertEquals(SentryLevel.ERROR, sentryEventLevelFor(Log.ERROR))
    }

    @Test
    fun fatal_capturesFatalEvent() {
        assertEquals(SentryLevel.FATAL, sentryEventLevelFor(LogLevel.Fatal.javaLevel))
    }

    @Test
    fun belowError_isBreadcrumbOnly() {
        for (priority in listOf(Log.VERBOSE, Log.DEBUG, Log.INFO, Log.WARN)) {
            assertNull("priority $priority", sentryEventLevelFor(priority))
        }
    }
}
