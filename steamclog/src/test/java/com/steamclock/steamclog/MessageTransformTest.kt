package com.steamclock.steamclog

import android.util.Log
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        // Only the log message and the two "Class: message" header lines, never the frames.
        assertEquals(
            setOf(
                "Loading https://host/path?code=from-message",
                "java.lang.IllegalStateException: Load failed for https://host/?code=from-exception",
                "java.io.IOException: GET https://host/path?code=from-cause failed"
            ),
            inputs.toSet()
        )
    }

    @Test
    fun consoleMessage_transformThatThrows_marksMessageAndExceptionHeader() {
        config.messageTransform = { throw IllegalStateException("transform failed in this test") }
        val exception = IOException("GET https://host/path?code=raw-exception-value failed")
        val wrapper = SteamclogThrowableWrapper("Loading https://host/path?code=raw-message-value", exception, false, null, null)

        val message = generateConsoleLogMessage(LogEvent(Log.ERROR, "unused", wrapper, callerTag = null))

        val markedStackTrace = exception.stackTraceToString().replace(
            exception.toString(), "$TRANSFORM_FAILED_PREFIX$exception"
        )
        assertEquals(
            "🚫 ${TRANSFORM_FAILED_PREFIX}Loading https://host/path?code=raw-message-value\n$markedStackTrace",
            message
        )
    }

    /** Transforms [throwable]'s printed stack trace the way the console and Sentry do. */
    private fun transformedStackTrace(throwable: Throwable): String =
        LogEvent(Log.ERROR, "Failed to load", throwable, callerTag = null).transformStackTrace(throwable.stackTraceToString())

    @Test
    fun stackTrace_headerThatStartsWithAnother_isCleanedWhole() {
        // #187 review: replacing "code=abc" first used to leave "code=<removed>def" in the longer line.
        config.messageTransform = removeCode
        val exception = IOException("GET https://host/?code=abc")
        exception.addSuppressed(IOException("GET https://host/?code=abcdef"))

        val stackTrace = transformedStackTrace(exception)

        assertFalse(stackTrace, stackTrace.contains("def"))
        assertEquals(2, Regex("code=<removed>").findAll(stackTrace).count())
    }

    @Test
    fun stackTrace_outerMessageThatRepeatsItsCause_isCleaned() {
        // #187 review: the old per-header replace left the outer line raw in about half of runs,
        // depending on map order, so this checks many separate exceptions.
        config.messageTransform = removeCode
        repeat(50) { run ->
            val cause = IOException("GET https://host/?code=cause-$run failed")
            val outer = IllegalStateException("Download https://host/?code=outer-$run failed: $cause", cause)

            val stackTrace = transformedStackTrace(outer)

            assertFalse(stackTrace, stackTrace.contains("code=outer-") || stackTrace.contains("code=cause-"))
        }
    }

    /** An exception whose stack trace header is its data class toString(), not its message. */
    private data class TokenException(val url: String) : Exception()

    @Test
    fun stackTrace_customToStringHeader_isCleaned() {
        config.messageTransform = removeCode
        val exception = TokenException("https://host/?code=from-data-class")

        val stackTrace = transformedStackTrace(exception)

        // The header is the data class toString(), "TokenException(url=...)", not a message line.
        assertTrue(stackTrace, stackTrace.startsWith("TokenException(url=https://host/?code=<removed>"))
        assertFalse(stackTrace.contains("from-data-class"))
    }

    @Test
    fun stackTrace_suppressedExceptionAddedAfterTheLogCall_isCleaned() {
        // #187 review: use {} adds the close() failure after a log-and-rethrow inside the block,
        // before the file thread renders the stack trace.
        config.messageTransform = removeCode
        val exception = IOException("Read failed")
        val event = LogEvent(Log.ERROR, "Failed to read", exception, callerTag = null)
        exception.addSuppressed(IOException("Close failed for https://host/?code=added-later"))

        val stackTrace = event.transformStackTrace(exception.stackTraceToString())

        assertTrue(stackTrace, stackTrace.contains("Close failed for https://host/?code=<removed>"))
        assertFalse(stackTrace.contains("added-later"))
    }

    @Test
    fun config_trailingLambda_stillSetsExtraInfo() {
        // #187 review: messageTransform is declared before extraInfo so this call keeps compiling.
        val trailingLambdaConfig = Config(isDebug = false, fileWritePath = null) { mapOf("build" to "123") }

        assertEquals(mapOf("build" to "123"), trailingLambdaConfig.extraInfo?.invoke(ExtraInfoPurpose.Error))
        assertNull(trailingLambdaConfig.messageTransform)
    }

    @Test
    fun fileLine_hasTheCallerTag() {
        tree.i("Line whose tag must be set")

        val line = destination.getLogFileContents().lines().single { it.endsWith("> Line whose tag must be set") }

        assertFalse(line, line.contains("[null]"))
    }
}
