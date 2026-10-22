package com.steamclock.steamclog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService

/**
 * Checks the log file writes that run on the destination's background thread (#151): reads wait
 * for pending writes, lines logged from several threads at once are all kept, including while a
 * file rotates (#155), flush waits for the queue and gives up after its timeout, and the crash
 * handler flushes before it hands off.
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
    fun logsFromManyThreads_duringRotation_areAllWrittenOnce() {
        // All 10 rotation files exist and have expired, so the first write must rotate: it empties
        // the oldest file and writes there. Before #151, two threads could both rotate at once and
        // empty the file under each other's lines (#155). Old contents may disappear; new lines
        // must not.
        val logDirectory = File(folder.root, "logs").apply { mkdirs() }
        val twoHoursAgo = System.currentTimeMillis() - 2 * 60 * 60 * 1000L
        for (index in 0 until 10) {
            File(logDirectory, "sclog_$index.txt").apply {
                writeText("old line from rotation file $index\n")
                setLastModified(twoHoursAgo - index * 60_000L) // sclog_9 is the oldest
            }
        }

        val threads = (1..8).map { thread ->
            Thread { (1..100).forEach { logInfo("rotating thread $thread line $it") } }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        val newMessages = destination.getLogFileContents().lines()
            .map { it.substringAfter("> ") }
            .filter { it.startsWith("rotating thread ") }

        val expected = (1..8).flatMap { thread -> (1..100).map { "rotating thread $thread line $it" } }
        assertEquals(expected.sorted(), newMessages.sorted())
    }

    @Test
    fun flush_waitsUntilPendingLinesAreOnDisk() {
        logInfo("line written before fatal closes the app")

        destination.flush(timeoutMs = 1000)

        assertTrue(linesOnDisk().any { it.endsWith("> line written before fatal closes the app") })
    }

    @Test
    fun flush_returnsAfterTimeout_withoutThrowing_whenTheFileThreadIsBusy() {
        // Hold the file thread with a task that waits on a latch, so flush cannot finish in time.
        // fileExecutor is private; reflection reads it through its lazy delegate.
        val executor = (destination.javaClass.getDeclaredField("fileExecutor\$delegate")
            .apply { isAccessible = true }.get(destination) as Lazy<*>).value as ExecutorService
        val release = CountDownLatch(1)
        executor.execute { release.await() }

        val start = System.nanoTime()
        destination.flush(timeoutMs = 100)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        release.countDown()

        assertTrue("flush took $elapsedMs ms", elapsedMs in 100..900)
    }

    @Test
    fun getLogFilesOrNull_returnsNullAfterTimeout_whenTheFileThreadIsBusy() {
        val executor = (destination.javaClass.getDeclaredField("fileExecutor\$delegate")
            .apply { isAccessible = true }.get(destination) as Lazy<*>).value as ExecutorService
        val release = CountDownLatch(1)
        executor.execute { release.await() }

        val files = destination.getLogFilesOrNull(LogSort.LastModifiedDesc, timeoutMs = 100)
        release.countDown()

        assertEquals(null, files)
    }

    @Test
    fun getLogFilesOrNull_onAnInterruptedThread_returnsNull_andKeepsTheInterruptFlag() {
        logInfo("line logged before an interrupted user report")
        Thread.currentThread().interrupt()

        val files = destination.getLogFilesOrNull(LogSort.LastModifiedDesc, timeoutMs = 1000)
        // Thread.interrupted() reads the flag and clears it, so later tests run uninterrupted.
        val stillInterrupted = Thread.interrupted()

        assertEquals(null, files)
        assertTrue(stillInterrupted)
    }

    @Test
    fun flush_onAnInterruptedThread_keepsTheInterruptFlag() {
        logInfo("line logged before an interrupted flush")
        Thread.currentThread().interrupt()

        destination.flush(timeoutMs = 1000)
        val stillInterrupted = Thread.interrupted()

        assertTrue(stillInterrupted)
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
