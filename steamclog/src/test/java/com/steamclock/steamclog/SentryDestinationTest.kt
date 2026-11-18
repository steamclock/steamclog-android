package com.steamclock.steamclog

import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber
import java.io.IOException

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
        // The config is shared by every test in the module; leave no transform behind (#182).
        clog.config.messageTransform = null
        transformFailureReported.set(false)
        Sentry.close()
    }

    @Test
    fun error_capturesErrorEvent() {
        clog.error("Failed to load")

        assertEquals(listOf(SentryLevel.ERROR), events.map { it.level })
        assertEquals("Failed to load", events.single().message?.formatted)
    }

    @Test
    fun errorWithThrowable_capturesSameEventAsErrorWithNullObject() {
        // Two instances, because Sentry drops a second event for a throwable it has already captured.
        val twoArgsThrowable = IllegalStateException("Throwable logged with error(message, throwable)")
        val threeArgsThrowable = IllegalStateException("Throwable logged with error(message, throwable, null)")

        clog.error("Failed to load", twoArgsThrowable)
        clog.error("Failed to load", threeArgsThrowable, null)

        assertEquals(2, events.size)
        val (twoArgs, threeArgs) = events
        assertEquals(threeArgs.level, twoArgs.level)
        assertEquals(threeArgs.message?.formatted, twoArgs.message?.formatted)
        assertSame(twoArgsThrowable, twoArgs.throwable)
        assertSame(threeArgsThrowable, threeArgs.throwable)
        assertEquals(threeArgs.extras, twoArgs.extras)
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
    fun messageTransform_appliesToEventMessageAndBreadcrumbs() {
        clog.config.messageTransform = { it.replace(Regex("code=[^&\\s]+"), "code=<removed>") }

        clog.info("Loading https://host/path?code=from-info-breadcrumb")
        Timber.i("Loading https://host/path?code=from-timber-breadcrumb")
        clog.error("Failed https://host/path?code=from-error", IOException("GET https://host/?code=from-exception"))

        val event = events.single()
        assertEquals("Failed https://host/path?code=<removed>", event.message?.formatted)
        val breadcrumbs = event.breadcrumbs!!.mapNotNull { it.message }
        assertTrue(breadcrumbs.contains("Loading https://host/path?code=<removed>"))
        // The info breadcrumb, the Timber breadcrumb, the error breadcrumb and the stack trace.
        assertEquals(4, breadcrumbs.count { it.contains("code=<removed>") })
        assertFalse(breadcrumbs.any { it.contains("code=from-") })
    }

    @Test
    fun messageTransform_runsOncePerText_forOneLogCall() {
        var calls = 0
        clog.config.messageTransform = { calls++; it }

        // The breadcrumb and the event message share the log message; the stack trace breadcrumb
        // transforms only the exception message.
        clog.error("Failed https://host/path?code=counted", IOException("GET https://host/?code=counted-exception"))

        assertEquals(2, calls)
    }

    @Test
    fun messageTransform_appliesToBlockedThrowableBreadcrumb() {
        clog.config.messageTransform = { it.replace(Regex("code=[^&\\s]+"), "code=<removed>") }
        clog.config.filtering = FilterOut { it is IOException }

        clog.error("Failed to load", IOException("GET https://host/?code=from-blocked-throwable"), null)
        // A blocked throwable sends no event, so a second error carries its breadcrumb to Sentry.
        clog.error("Error logged after the blocked throwable")

        val breadcrumbs = events.single().breadcrumbs!!.mapNotNull { it.message }
        assertTrue(breadcrumbs.contains(
            "IOException on blocked list, and has been blocked from being captured as an exception: " +
                    "GET https://host/?code=<removed>"
        ))
    }

    @Test
    fun messageTransformThatThrows_sendsRawTextMarked_andReportsFirstFailureOnce() {
        clog.config.messageTransform = { throw IllegalStateException("transform failed in this test") }
        val firstMessage = "Loading https://host/path?code=raw-breadcrumb"

        clog.info(firstMessage)
        clog.error("Failed https://host/path?code=raw-error", IOException("GET https://host/?code=raw-exception"))

        val (report, errorEvent) = events
        assertEquals(2, events.size)
        assertEquals(SentryLevel.WARNING, report.level)
        val reportText = report.message?.formatted!!
        assertTrue(reportText, reportText.contains("java.lang.IllegalStateException on ${firstMessage.length} characters"))
        assertFalse(reportText.contains("raw-"))

        assertEquals("${TRANSFORM_FAILED_PREFIX}Failed https://host/path?code=raw-error", errorEvent.message?.formatted)
        val breadcrumbs = errorEvent.breadcrumbs!!.mapNotNull { it.message }
        assertTrue(breadcrumbs.contains("${TRANSFORM_FAILED_PREFIX}Loading https://host/path?code=raw-breadcrumb"))
        // The marker goes in front of the whole "Class: message" header line.
        assertTrue(breadcrumbs.any { it.startsWith("${TRANSFORM_FAILED_PREFIX}java.io.IOException: GET") })
    }

    /** An object logged with an error; its redacted description becomes the line's extra data. */
    private class LoggedRequest(val url: String)

    @Test
    fun errorWithObject_eventMessageIsTransformed_withoutTheObjectData() {
        clog.config.messageTransform = { it.replace(Regex("code=[^&\\s]+"), "code=<removed>") }

        clog.error("Failed https://host/path?code=from-error-with-object now", null, LoggedRequest("https://host/?code=from-object"))

        val event = events.single()
        assertEquals("Failed https://host/path?code=<removed> now", event.message?.formatted)
        val breadcrumbs = event.breadcrumbs!!.mapNotNull { it.message }
        assertTrue(breadcrumbs.toString(), breadcrumbs.any { it.startsWith("Failed https://host/path?code=<removed> now: ") })
        assertFalse(breadcrumbs.any { it.contains("code=from-") })
    }

    @Test
    fun messageTransformThatThrows_underDebugPreset_reportsNothingToSentry() {
        // The Debug presets send nothing to Sentry (remote level None), so neither does the report.
        clog.config.logLevel = LogLevelPreset.Debug
        clog.config.messageTransform = { throw IllegalStateException("transform failed in this test") }

        clog.info("Loading https://host/path?code=logged-under-debug-preset")

        assertTrue(events.isEmpty())
        assertFalse(transformFailureReported.get())
    }

    @Test
    fun blockedThrowable_capturesNoEvent() {
        clog.config.filtering = FilterOut { it is IllegalStateException }

        clog.error("Failed to load", IllegalStateException("Blocked throwable logged with clog.error"), null)
        Timber.wtf(IllegalStateException("Blocked throwable logged at fatal priority"), "Cannot recover")

        assertTrue(events.isEmpty())
    }
}
