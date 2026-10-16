package com.steamclock.steamclog

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import timber.log.Timber

/**
 * Checks that calling initWith more than once plants the log file destination only once (#168).
 */
class InitWithTest {

    @get:Rule
    val folder = TemporaryFolder()

    // Release has no console level, so nothing calls android.util.Log.
    private fun config() = Config(isDebug = false, fileWritePath = folder.root, logLevel = LogLevelPreset.Release)

    @After
    fun tearDown() {
        // SteamcLog is shared by every test in the module; leave no file destination pointing at
        // the deleted temporary folder.
        Timber.forest().filterIsInstance<ExternalLogFileDestination>().forEach { Timber.uproot(it) }
    }

    @Test
    fun secondInitWith_doesNotPlantAnotherTree() {
        clog.initWith(config())
        val treeCountAfterFirstInit = Timber.treeCount

        clog.initWith(config())

        assertEquals(treeCountAfterFirstInit, Timber.treeCount)
        assertEquals(1, Timber.forest().filterIsInstance<ExternalLogFileDestination>().size)
    }
}
