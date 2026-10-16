package com.steamclock.steamclog

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Checks which log files ExternalLogFileDestination.removeOldLogFiles deletes (#147). Each test
 * passes the directory, day count and time, so SteamcLog.config is never read.
 */
class LogFileExpiryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val now = System.currentTimeMillis()
    private val dayMs = 86_400_000L

    private fun logFile(name: String, ageDays: Int): File {
        return folder.newFile(name).apply { setLastModified(now - ageDays * dayMs) }
    }

    private fun removeOldLogFiles(keepLogsForDays: Int) {
        ExternalLogFileDestination().removeOldLogFiles(folder.root, keepLogsForDays, now)
    }

    private fun remainingFiles(): Set<String> = folder.root.list()!!.toSet()

    @Test
    fun deletesOnlyFilesOlderThanKeepDays() {
        logFile("current.txt", ageDays = 0)
        logFile("recent.txt", ageDays = 2)
        logFile("old.txt", ageDays = 4)

        removeOldLogFiles(keepLogsForDays = 3)

        assertEquals(setOf("current.txt", "recent.txt"), remainingFiles())
    }

    @Test
    fun keepDaysPastIntMillisLimit_keepsRecentFiles() {
        // 25 days in milliseconds is larger than Int.MAX_VALUE.
        logFile("current.txt", ageDays = 0)
        logFile("recent.txt", ageDays = 24)
        logFile("old.txt", ageDays = 26)

        removeOldLogFiles(keepLogsForDays = 25)

        assertEquals(setOf("current.txt", "recent.txt"), remainingFiles())
    }

    @Test
    fun keepDaysOfTenYears_keepsYearOldFile() {
        logFile("current.txt", ageDays = 0)
        logFile("old.txt", ageDays = 365)

        removeOldLogFiles(keepLogsForDays = 3650)

        assertEquals(setOf("current.txt", "old.txt"), remainingFiles())
    }

    @Test
    fun keepDaysIntMax_keepsAllFiles() {
        logFile("current.txt", ageDays = 0)
        logFile("old.txt", ageDays = 365)

        removeOldLogFiles(keepLogsForDays = Int.MAX_VALUE)

        assertEquals(setOf("current.txt", "old.txt"), remainingFiles())
    }
}
