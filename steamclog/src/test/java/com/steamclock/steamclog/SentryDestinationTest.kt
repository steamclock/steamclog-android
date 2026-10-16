package com.steamclock.steamclog

import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber

/**
 * Checks which logs SentryDestination turns into Sentry events (#142). Sentry is started with a
 * beforeSend callback that records each event and drops it, so nothing is sent.
 *
 * clog.fatal is not called here because it ends the process. Timber.wtf logs at the same priority
 * (Log.ASSERT) through the same destination.
 */
class SentryDestinationTest {

    private val events = mutableListOf<SentryEvent>()

    @Before
    fun setUp() {
        Sentry.init { options ->
            options.dsn = "https://key@example.invalid/1"
            options.setBeforeSend { event, _ ->
                events += event
                null
            }
        }
        // ReleaseAdvanced sends Debug and above to the remote destination, so every level below
        // error reaches SentryDestination. Its console level is None, so nothing calls
        // android.util.Log, which is not available in JVM unit tests.
        clog.initWith(Config(isDebug = false, fileWritePath = null, logLevel = LogLevelPreset.ReleaseAdvanced))
        events.clear()
    }

    @After
    fun tearDown() {
        Sentry.close()
    }

    @Test
    fun error_capturesErrorEvent() {
        clog.error("Failed to load")

        assertEquals(listOf(SentryLevel.ERROR), events.map { it.level })
        assertEquals("Failed to load", events.single().message?.formatted)
    }

    @Test
    fun fatalPriority_capturesFatalEvent() {
        Timber.wtf("Cannot recover")

        assertEquals(listOf(SentryLevel.FATAL), events.map { it.level })
    }

    @Test
    fun belowError_capturesNoEvent() {
        clog.debug("debug")
        clog.info("info")
        clog.warn("warn")

        assertTrue(events.isEmpty())
    }

    @Test
    fun blockedThrowable_capturesNoEvent() {
        clog.config.filtering = FilterOut { it is IllegalStateException }

        clog.error("Failed to load", IllegalStateException("boom"), null)
        Timber.wtf(IllegalStateException("boom"), "Cannot recover")

        assertTrue(events.isEmpty())
    }
}
