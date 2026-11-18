package com.steamclock.steamclog

import android.util.Log
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

/**
 * Checks that Config.messageTransform changes the text the log file and the console get, for
 * lines logged through SteamcLog and through Timber directly, and that a transform that throws
 * never lets the raw text through (#187). SentryDestinationTest covers Sentry.
 *
 * Like LogFileWriteTest, each test uses its own ExternalLogFileDestination and sets the config
 * with reflection. No test here logs a throwable to the file: Log.getStackTraceString returns
 * null in JVM unit tests. LogFileStrictModeTest covers the stack trace in the file on a device.
 */
class MessageTransformTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val destination = ExternalLogFileDestination()
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

        destination.i(wrapper)

        assertEquals(
            listOf("Loading https://host/path?code=<removed> now: { url: https://host/?code=<removed> }"),
            loggedMessages()
        )
    }

    @Test
    fun directTimberLine_isTransformed_inLogFile() {
        config.messageTransform = removeCode

        destination.i("Loading https://host/path?code=from-timber")

        assertEquals(listOf("Loading https://host/path?code=<removed>"), loggedMessages())
    }

    @Test
    fun transformThatThrowsAnException_writesPlaceholder_notRawLine() {
        config.messageTransform = { throw IllegalStateException("transform failed in this test") }

        destination.i("Loading https://host/path?code=raw-value-exception")

        assertEquals(listOf(TRANSFORM_FAILED_PLACEHOLDER), loggedMessages())
    }

    @Test
    fun transformThatThrowsAnError_writesPlaceholder_notRawLine() {
        config.messageTransform = { throw StackOverflowError("transform overflowed in this test") }

        destination.i("Loading https://host/path?code=raw-value-error")

        assertEquals(listOf(TRANSFORM_FAILED_PLACEHOLDER), loggedMessages())
    }

    @Test
    fun noTransform_leavesLineUnchanged() {
        destination.i("Loading https://host/path?code=kept-without-transform")

        assertEquals(listOf("Loading https://host/path?code=kept-without-transform"), loggedMessages())
    }

    @Test
    fun consoleMessage_transformsMessageAndStackTrace_butNotEmoji() {
        config.messageTransform = removeCode
        val exception = IOException("GET https://host/path?code=from-exception failed")
        val wrapper = SteamclogThrowableWrapper("Loading https://host/path?code=from-message", exception, false, null, null)

        val message = generateConsoleLogMessage(Log.ERROR, wrapper, "unused")

        assertEquals(
            "🚫 Loading https://host/path?code=<removed>\n${removeCode(exception.stackTraceToString())}",
            message
        )
    }

    @Test
    fun consoleMessage_transformThatThrows_hidesMessageAndStackTrace() {
        config.messageTransform = { throw IllegalStateException("transform failed in this test") }
        val exception = IOException("GET https://host/path?code=raw-exception-value failed")
        val wrapper = SteamclogThrowableWrapper("Loading https://host/path?code=raw-message-value", exception, false, null, null)

        val message = generateConsoleLogMessage(Log.ERROR, wrapper, "unused")

        assertEquals("🚫 $TRANSFORM_FAILED_PLACEHOLDER\n$TRANSFORM_FAILED_PLACEHOLDER", message)
        assertFalse(message.contains("raw-"))
    }
}
