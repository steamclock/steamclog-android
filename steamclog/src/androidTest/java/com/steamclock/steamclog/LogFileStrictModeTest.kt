package com.steamclock.steamclog

import android.os.StrictMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Checks on a device that logging from the main thread does no disk I/O there (#151).
 * StrictMode's penaltyDeath ends the test process on the first disk read or write on the main
 * thread, so before #151 the first logged line failed this test.
 * Doc: docs/evergreen/log-files.md — log calls never touch the disk on the calling thread.
 */
@RunWith(AndroidJUnit4::class)
class LogFileStrictModeTest {

    companion object {
        // One directory for every test: the SteamcLog singleton keeps its current log file for
        // up to 10 minutes, so a second initWith with a new directory would still write to the
        // first one (see #182).
        private val logDirectory: File by lazy {
            val cacheDir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
            File(cacheDir, "strict-mode-test-${System.nanoTime()}")
        }
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @After
    fun tearDown() {
        Sentry.close()
    }

    /** Runs [block] on the main thread with StrictMode set to kill the process on disk I/O. */
    private fun onMainThreadUnderStrictMode(block: () -> Unit) {
        instrumentation.runOnMainSync {
            val previousPolicy = StrictMode.getThreadPolicy()
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().penaltyLog().penaltyDeath().build()
            )
            try {
                block()
            } finally {
                StrictMode.setThreadPolicy(previousPolicy)
            }
        }
    }

    @Test
    fun logging_onMainThread_doesNoDiskIo_andTheLinesReachTheFile() {
        val directory = logDirectory // outside StrictMode: reading cacheDir checks the disk
        val messages = (1..50).map { "line logged on the main thread under StrictMode $it" }

        onMainThreadUnderStrictMode {
            clog.initWith(Config(isDebug = true, fileWritePath = directory, logLevel = LogLevelPreset.Debug))
            messages.forEach { clog.info(it) }
        }

        // Off the main thread: getFullLogContents waits for the queued writes, then reads them.
        val contents = clog.getFullLogContents() ?: ""
        for (message in messages) {
            assertTrue("missing: $message", contents.contains(message))
        }
    }

    private fun releaseConfig(directory: File) = Config(
        isDebug = false,
        fileWritePath = directory,
        logLevel = LogLevelPreset.Release, // file and Sentry at Info, console off
        detailedLogsOnUserReports = true
    )

    @Test
    fun releasePreset_withSentry_breadcrumbsOnMainThread_doNoDiskIo() {
        val directory = logDirectory // outside StrictMode: reading cacheDir checks the disk
        // Sentry is set up outside StrictMode: its own startup reads and writes its cache. The DSN
        // points at a host that does not exist, so nothing leaves the device.
        instrumentation.runOnMainSync {
            SentryAndroid.init(instrumentation.targetContext) { options ->
                options.dsn = "https://key@example.invalid/1"
            }
        }
        val messages = (1..20).map { "line logged with the Release preset and Sentry on $it" }

        // Info logs only: they become Sentry breadcrumbs. An error or user report would make
        // Sentry capture an event, and Sentry reads device files (/sys) on the calling thread
        // while doing that (#183), which is outside what this test checks.
        onMainThreadUnderStrictMode {
            clog.initWith(releaseConfig(directory))
            messages.forEach { clog.info(it) }
        }

        val contents = clog.getFullLogContents() ?: ""
        for (message in messages) {
            assertTrue("missing: $message", contents.contains(message))
        }
    }

    @Test
    fun releasePreset_errorAndUserReportWithAttachments_onMainThread_doNoSteamcLogDiskIo() {
        val directory = logDirectory // outside StrictMode: reading cacheDir checks the disk
        // Without Sentry set up, Sentry's calls do nothing, so this checks SteamcLog's own work on
        // the error and user report paths, including listing the files to attach (rule 5).
        Sentry.close()
        val userReportMessage = "user report logged by the Release preset StrictMode test"

        onMainThreadUnderStrictMode {
            clog.initWith(releaseConfig(directory))
            clog.error("error logged by the Release preset StrictMode test", IllegalStateException("raised by this test"))
            clog.userReport(userReportMessage)
        }

        val contents = clog.getFullLogContents() ?: ""
        assertTrue(contents.contains(userReportMessage))
    }
}
