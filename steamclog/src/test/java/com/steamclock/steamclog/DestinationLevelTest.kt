package com.steamclock.steamclog

import android.util.Log
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Checks which priorities each destination and SteamclogTree take after initWith (#187). Since the
 * destinations stopped being planted trees, these checks decide what reaches each destination.
 *
 * The config is set with reflection, as in LogFileWriteTest, so no test plants anything.
 */
class DestinationLevelTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val console = ConsoleDestination()
    private val sentry = SentryDestination()
    private val file = ExternalLogFileDestination()
    private val priorities = Log.VERBOSE..Log.ASSERT

    private fun setConfig(preset: LogLevelPreset, fileWritePath: File?) {
        val config = Config(isDebug = false, fileWritePath = fileWritePath, logLevel = preset)
        SteamcLog::class.java.getDeclaredField("config").apply { isAccessible = true }.set(null, config)
    }

    @After
    fun tearDown() {
        // The config is shared by every test in the module; leave none pointing at the deleted folder.
        SteamcLog::class.java.getDeclaredField("config").apply { isAccessible = true }.set(null, null)
    }

    /** The lowest priority [destination] takes, or null if it takes none. */
    private fun lowestAccepted(destination: LogDestination): Int? = priorities.firstOrNull { destination.accepts(it) }

    @Test
    fun releasePreset_withFilePath() {
        setConfig(LogLevelPreset.Release, folder.root)

        assertEquals(null, lowestAccepted(console))
        assertEquals(Log.INFO, lowestAccepted(file))
        assertEquals(Log.INFO, lowestAccepted(sentry))
    }

    @Test
    fun debugPreset_withFilePath() {
        setConfig(LogLevelPreset.Debug, folder.root)

        assertEquals(Log.DEBUG, lowestAccepted(console))
        assertEquals(Log.DEBUG, lowestAccepted(file))
        assertEquals(null, lowestAccepted(sentry))
    }

    @Test
    fun fileDestination_withoutFilePath_takesNothing() {
        setConfig(LogLevelPreset.DebugVerbose, fileWritePath = null)

        assertEquals(null, lowestAccepted(file))
    }

    @Test
    fun steamclogTree_isLoggable_whenAnyDestinationTakesThePriority() {
        setConfig(LogLevelPreset.Release, fileWritePath = null)
        val tree = SteamclogTree(listOf(console, sentry, file))
        // isLoggable is protected in Timber.Tree.
        val isLoggable = SteamclogTree::class.java.getDeclaredMethod("isLoggable", Int::class.java)
            .apply { isAccessible = true }

        // Release without a file path: only Sentry takes logs, from Info up.
        assertFalse(isLoggable.invoke(tree, Log.DEBUG) as Boolean)
        assertTrue(isLoggable.invoke(tree, Log.INFO) as Boolean)
    }
}
