package com.steamclock.steamclog

import android.os.StrictMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

    @Test
    fun logging_onMainThread_doesNoDiskIo_andTheLinesReachTheFile() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val logDirectory = File(instrumentation.targetContext.cacheDir, "strict-mode-test-${System.nanoTime()}")
        val messages = (1..50).map { "line logged on the main thread under StrictMode $it" }

        instrumentation.runOnMainSync {
            val previousPolicy = StrictMode.getThreadPolicy()
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().penaltyLog().penaltyDeath().build()
            )
            try {
                clog.initWith(Config(isDebug = true, fileWritePath = logDirectory, logLevel = LogLevelPreset.Debug))
                messages.forEach { clog.info(it) }
            } finally {
                StrictMode.setThreadPolicy(previousPolicy)
            }
        }

        // Off the main thread: getFullLogContents waits for the queued writes, then reads them.
        val contents = clog.getFullLogContents() ?: ""
        for (message in messages) {
            assertTrue("missing: $message", contents.contains(message))
        }
    }
}
