package com.steamclock.steamclog

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Checks which log files removeLogFilesOlderThan deletes (#147).
 */
class LogFileExpiryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val now = System.currentTimeMillis()
    private val dayMs = 86_400_000L

    private fun logFile(name: String, ageDays: Int): File {
        return folder.newFile(name).apply { setLastModified(now - ageDays * dayMs) }
    }

    private fun remainingFiles(): Set<String> = folder.root.list()!!.toSet()

    @Test
    fun deletesOnlyFilesOlderThanKeepDays() {
        logFile("current.txt", ageDays = 0)
        logFile("recent.txt", ageDays = 2)
        logFile("old.txt", ageDays = 4)

        removeLogFilesOlderThan(folder.root, keepLogsForDays = 3, now)

        assertEquals(setOf("current.txt", "recent.txt"), remainingFiles())
    }

    @Test
    fun keepDaysPastIntMillisLimit_keepsRecentFiles() {
        // 25 days in milliseconds is larger than Int.MAX_VALUE.
        logFile("current.txt", ageDays = 0)
        logFile("recent.txt", ageDays = 24)
        logFile("old.txt", ageDays = 26)

        removeLogFilesOlderThan(folder.root, keepLogsForDays = 25, now)

        assertEquals(setOf("current.txt", "recent.txt"), remainingFiles())
    }

    @Test
    fun keepDaysOfTenYears_keepsYearOldFile() {
        logFile("current.txt", ageDays = 0)
        logFile("old.txt", ageDays = 365)

        removeLogFilesOlderThan(folder.root, keepLogsForDays = 3650, now)

        assertEquals(setOf("current.txt", "old.txt"), remainingFiles())
    }

    @Test
    fun keepDaysIntMax_keepsAllFiles() {
        logFile("current.txt", ageDays = 0)
        logFile("old.txt", ageDays = 365)

        removeLogFilesOlderThan(folder.root, keepLogsForDays = Int.MAX_VALUE, now)

        assertEquals(setOf("current.txt", "old.txt"), remainingFiles())
    }
}
