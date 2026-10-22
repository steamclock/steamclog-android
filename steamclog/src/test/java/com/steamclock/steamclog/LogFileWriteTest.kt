package com.steamclock.steamclog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Checks the log file writes that run on the destination's background thread (#151): reads wait
 * for pending writes, concurrent logs are all kept (#155), and flush waits for the queue.
 *
 * Each test uses its own ExternalLogFileDestination rather than initWith, so no test shares the
 * SteamcLog singleton's cached log file with another. setUp sets the config with reflection
 * because the destination reads fileWritePath from it.
 */
class LogFileWriteTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val destination = ExternalLogFileDestination()

    @Before
    fun setUp() {
        val config = Config(isDebug = false, fileWritePath = folder.root, logLevel = LogLevelPreset.Release)
        SteamcLog::class.java.getDeclaredField("config").apply { isAccessible = true }.set(null, config)
    }

    // Timber's public entry point, so the call goes through isLoggable and the destination's log.
    private fun logInfo(message: String) = destination.i(message)

    /** Reads the log files straight from disk, without going through the file thread. */
    private fun linesOnDisk(): List<String> =
        File(folder.root, "logs").listFiles()!!.sortedBy { it.lastModified() }.flatMap { it.readLines() }

    @Test
    fun getLogFileContents_includesEveryLineLoggedBeforeIt_inOrder() {
        val messages = (1..200).map { "ordered line $it" }
        messages.forEach { logInfo(it) }

        val loggedMessages = destination.getLogFileContents().lines().filter { it.isNotEmpty() }
            .map { it.substringAfter("> ") }

        assertEquals(messages, loggedMessages)
    }

    @Test
    fun logsFromManyThreads_areAllWrittenOnce() {
        val threads = (1..8).map { thread ->
            Thread { (1..100).forEach { logInfo("thread $thread line $it") } }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        val loggedMessages = destination.getLogFileContents().lines().filter { it.isNotEmpty() }
            .map { it.substringAfter("> ") }

        val expected = (1..8).flatMap { thread -> (1..100).map { "thread $thread line $it" } }
        assertEquals(expected.sorted(), loggedMessages.sorted())
    }

    @Test
    fun flush_waitsUntilPendingLinesAreOnDisk() {
        logInfo("line written before fatal closes the app")

        destination.flush(timeoutMs = 1000)

        assertTrue(linesOnDisk().any { it.endsWith("> line written before fatal closes the app") })
    }

    @Test
    fun uncaughtExceptionHandler_flushesPendingLines_thenCallsPreviousHandler() {
        var previousHandlerSaw: Throwable? = null
        val handler = SteamcLog.flushingUncaughtExceptionHandler(destination) { _, throwable ->
            // The previous handler (Sentry's, in an app) runs after the flush.
            assertTrue(linesOnDisk().any { it.endsWith("> line logged just before the crash") })
            previousHandlerSaw = throwable
        }
        val crash = IllegalStateException("uncaught exception raised by this test")

        logInfo("line logged just before the crash")
        handler.uncaughtException(Thread.currentThread(), crash)

        assertEquals(crash, previousHandlerSaw)
    }

    @Test
    fun writtenLine_namesTheThreadThatLogged_notTheFileThread() {
        val loggingThread = Thread({ logInfo("line from a named thread") }, "test-logging-thread")
        loggingThread.start()
        loggingThread.join()

        val line = destination.getLogFileContents().lines().single { it.endsWith("> line from a named thread") }

        assertTrue(line, line.contains(":test-logging-thread]"))
    }
}
