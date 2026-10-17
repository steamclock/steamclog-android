package com.steamclock.steamclog

import android.util.Log
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryEvent
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber

/**
 * Checks that a log made before initWith is dropped and does not throw (#169).
 *
 * SteamcLog is shared by every test in the module, and other tests call initWith. setUp clears
 * the config with reflection, so these tests do not depend on test order.
 */
class BeforeInitWithTest {

    private val events = mutableListOf<SentryEvent>()
    private val breadcrumbs = mutableListOf<Breadcrumb>()

    @Before
    fun setUp() {
        SteamcLog::class.java.getDeclaredField("config").apply { isAccessible = true }.set(null, null)
        Sentry.init { options ->
            options.dsn = "https://key@example.invalid/1"
            options.setBeforeSend { event, _ ->
                events += event
                null
            }
            options.setBeforeBreadcrumb { breadcrumb, _ ->
                breadcrumbs += breadcrumb
                null
            }
        }
    }

    @After
    fun tearDown() {
        Sentry.close()
    }

    @Test
    fun clogCalls_beforeInitWith_doNotThrow() {
        clog.info("x")
        clog.error("x", IllegalStateException(), Any())
        clog.userReport("x", IllegalStateException(), Any())
    }

    @Test
    fun directTimberCall_beforeInitWith_doesNotThrow() {
        Timber.e("x")
        Timber.e(IllegalStateException(), "x")
    }

    @Test
    fun destinations_beforeInitWith_logNothing() {
        val destinations = listOf(ConsoleDestination(), SentryDestination(), ExternalLogFileDestination())
        for (destination in destinations) {
            // isLoggable is protected in Timber.Tree.
            val isLoggable = destination.javaClass.getDeclaredMethod("isLoggable", Int::class.java)
                .apply { isAccessible = true }
            for (priority in Log.VERBOSE..Log.ASSERT) {
                assertFalse(isLoggable.invoke(destination, priority) as Boolean)
            }
        }
    }

    @Test
    fun logs_beforeInitWith_reachNoSentryEventOrBreadcrumb() {
        clog.error("x", IllegalStateException(), Any())
        Timber.e("x")

        assertTrue(events.isEmpty())
        assertTrue(breadcrumbs.isEmpty())
    }
}
