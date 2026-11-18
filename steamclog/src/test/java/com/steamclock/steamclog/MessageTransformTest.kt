package com.steamclock.steamclog

import android.util.Log
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

/**
 * Checks that Config.messageTransform changes the text the log file and the console get, for
 * lines logged through SteamcLog and through Timber directly, that it runs once per log call
 * whatever the number of destinations, and that a transform that throws leaves the raw text
 * marked as unscrubbed (#187). SentryDestinationTest covers Sentry.
 *
 * Like LogFileWriteTest, each test uses its own ExternalLogFileDestination and sets the config
 * with reflection. No test here logs a throwable to the file: Log.getStackTraceString returns
 * null in JVM unit tests. LogFileStrictModeTest covers the stack trace in the file on a device.
 */
class MessageTransformTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val destination = ExternalLogFileDestination()
    private val tree = SteamclogTree(listOf(destination))
    private lateinit var config: Config

    @Before
    fun setUp() {
        config = Config(isDebug = false, fileWritePath = folder.root, logLevel = LogLevelPreset.Release)
        SteamcLog::class.java.getDeclaredField("config").apply { isAccessible = true }.set(null, config)
    }

    @After
    fun tearDown() {
        // The config is shared by every test in the module; leave no transform behind (#182).
        config.messageTransform = null
        transformFailureReported.set(false)
    }

    private val removeCode: (String) -> String = { it.replace(Regex("code=[^&\\s]+"), "code=<removed>") }

    /** The text after the tag of each line in the log file. */
    private fun loggedMessages(): List<String> =
        destination.getLogFileContents().lines().filter { it.isNotEmpty() }.map { it.substringAfter("> ") }

    @Test
    fun clogLine_isTransformed_inLogFile_includingObjectData() {
        config.messageTransform = removeCode
        // What clog.info(message, obj) passes to each destination.
        val wrapper = SteamclogThrowableWrapper(
            "Loading https://host/path?code=from-clog-message now", null, false, "{ url: https://host/?code=from-object }", null
        )

        tree.i(wrapper)

        assertEquals(
            listOf("Loading https://host/path?code=<removed> now: { url: https://host/?code=<removed> }"),
            loggedMessages()
        )
    }

    @Test
    fun directTimberLine_isTransformed_inLogFile() {
        config.messageTransform = removeCode

        tree.i("Loading https://host/path?code=from-timber")

        assertEquals(listOf("Loading https://host/path?code=<removed>"), loggedMessages())
    }

    @Test
    fun transformThatThrowsAnException_writesRawLine_markedUnscrubbed() {
        config.messageTransform = { throw IllegalStateException("transform failed in this test") }

        tree.i("Loading https://host/path?code=raw-value-exception")

        assertEquals(
            listOf("${TRANSFORM_FAILED_PREFIX}Loading https://host/path?code=raw-value-exception"),
            loggedMessages()
        )
    }

    @Test
    fun transformThatThrowsAnError_writesRawLine_markedUnscrubbed() {
        config.messageTransform = { throw StackOverflowError("transform overflowed in this test") }

        tree.i("Loading https://host/path?code=raw-value-error")

        assertEquals(
            listOf("${TRANSFORM_FAILED_PREFIX}Loading https://host/path?code=raw-value-error"),
            loggedMessages()
        )
    }

    /** A destination that takes every log and keeps the text it is given. */
    private class RecordingDestination : LogDestination {
        val texts = mutableListOf<String>()
        override val needsCallerTag = false
        override fun accepts(priority: Int) = true
        override fun write(event: LogEvent) { texts += event.text }
    }

    @Test
    fun oneLogCall_isTransformedOnce_forEveryDestination() {
        var calls = 0
        config.messageTransform = { calls++; removeCode(it) }
        val first = RecordingDestination()
        val second = RecordingDestination()
        val tree = SteamclogTree(listOf(destination, first, second))

        tree.i("Loading https://host/path?code=transformed-once")

        assertEquals(1, calls)
        assertEquals(listOf("Loading https://host/path?code=<removed>"), first.texts)
        assertEquals(first.texts, second.texts)
        assertEquals(first.texts, loggedMessages())
    }

    @Test
    fun noTransform_leavesLineUnchanged() {
        tree.i("Loading https://host/path?code=kept-without-transform")

        assertEquals(listOf("Loading https://host/path?code=kept-without-transform"), loggedMessages())
    }

    @Test
    fun consoleMessage_transformsMessageAndExceptionMessages_butNotEmojiOrFrames() {
        val inputs = mutableListOf<String>()
        config.messageTransform = { inputs += it; removeCode(it) }
        val cause = IOException("GET https://host/path?code=from-cause failed")
        val exception = IllegalStateException("Load failed for https://host/?code=from-exception", cause)
        val wrapper = SteamclogThrowableWrapper("Loading https://host/path?code=from-message", exception, false, null, null)

        val message = generateConsoleLogMessage(LogEvent(Log.ERROR, "unused", wrapper, callerTag = null))

        assertEquals(
            "🚫 Loading https://host/path?code=<removed>\n${removeCode(exception.stackTraceToString())}",
            message
        )
        // Only the log message and the two exception messages, never the frames.
        assertEquals(
            setOf(
                "Loading https://host/path?code=from-message",
                "Load failed for https://host/?code=from-exception",
                "GET https://host/path?code=from-cause failed"
            ),
            inputs.toSet()
        )
    }

    @Test
    fun consoleMessage_transformThatThrows_marksMessageAndExceptionMessage() {
        config.messageTransform = { throw IllegalStateException("transform failed in this test") }
        val exception = IOException("GET https://host/path?code=raw-exception-value failed")
        val wrapper = SteamclogThrowableWrapper("Loading https://host/path?code=raw-message-value", exception, false, null, null)

        val message = generateConsoleLogMessage(LogEvent(Log.ERROR, "unused", wrapper, callerTag = null))

        val markedStackTrace = exception.stackTraceToString().replace(
            "IOException: GET", "IOException: ${TRANSFORM_FAILED_PREFIX}GET"
        )
        assertEquals(
            "🚫 ${TRANSFORM_FAILED_PREFIX}Loading https://host/path?code=raw-message-value\n$markedStackTrace",
            message
        )
    }
}
