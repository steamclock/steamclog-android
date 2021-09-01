package com.steamclock.steamclog

import android.annotation.SuppressLint
import android.util.Log
import io.sentry.Sentry
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Destinations
 *
 * Created by shayla on 2020-01-23
 */

//-----------------------------------------------------------------------------
// Default Destination Trees
//
// Each destination uses a Steamclog.config.destinationLevels setting to determine if
// they are to consume the logged item or not.
//-----------------------------------------------------------------------------
/**
 * SentryDestination
 */
internal class SentryDestination : Timber.Tree() {

    companion object {
        const val breadcrumbCategory = "steamclog"
    }

    override fun isLoggable(priority: Int): Boolean {
        return isLoggable(SteamcLog.config.logLevel.sentry, priority)
    }

    /**
     * From Sentry docs: By default, the last 100 breadcrumbs are kept and attached to next event.
     */
    override fun log(priority: Int, tag: String?, message: String, throwable: Throwable?) {
        val wrapper = SteamclogThrowableWrapper.from(throwable)
        val originalMessage = wrapper?.originalMessage ?: message
        val originalThrowable = wrapper?.originalThrowable

        // Always add breadcrumb that indicates the log message.
        Sentry.addBreadcrumb(generateSimpleLogMessage(
            priority,
            includeEmoji = false,
            wrapper,
            message
        ), breadcrumbCategory)

        when {
            priority == Log.ERROR && originalThrowable != null -> {
                // Check to see if we want to allow or block the Throwable from being reported
                // as an error.
                if (SteamcLog.throwableBlocker.shouldBlock(originalThrowable)) {
                    Sentry.addBreadcrumb("${originalThrowable::class.simpleName} on blocked list, and has " +
                            "been blocked from being captured as an exception: " +
                            "${originalThrowable.message}", breadcrumbCategory)
                } else {
                    Sentry.captureException(originalThrowable)
                }
            }
            priority == Log.ERROR -> {
                // If no throwable given, capture message as the error
                Sentry.captureMessage(originalMessage)
            }
            else -> {
                // Not an error; breadcrumb should already be added, so we do not
                // need to do anything more.
            }
        }
    }
}

/**
 * ConsoleDestination
 * DebugTree gives us access to override createStackElementTag
 */
internal class ConsoleDestination: Timber.DebugTree() {

    override fun isLoggable(priority: Int): Boolean {
        return isLoggable(SteamcLog.config.logLevel.console, priority)
    }

    override fun log(priority: Int, tag: String?, message: String, throwable: Throwable?) {
        val wrapper = SteamclogThrowableWrapper.from(throwable)
        val originalThrowable = wrapper?.originalThrowable
        val fullMessage = generateSimpleLogMessage(
            priority,
            includeEmoji = true,
            throwable,
            message)

        // Since we are relying on android.util.log formatting here,
        // do not call generateFormattedLogMessage
        super.log(priority, createCustomStackElementTag(), fullMessage, originalThrowable)
    }
}

/**
 * ExternalLogFileDestination
 * DebugTree gives us access to override createStackElementTag
 */
internal class ExternalLogFileDestination : Timber.DebugTree() {
    private var fileNamePrefix: String = "sclog"
    private var fileExt = "txt"

    private var rotatingIndexes = (0 until 10).map { it }

    private var cachedCurrentLogFile: File? = null
    private var currentLogFileCachedTime: Long? = null

    override fun isLoggable(priority: Int): Boolean {
        return isLoggable(SteamcLog.config.logLevel.file, priority)
    }

    //---------------------------------------------
    // Allows us to print out to an external file if desired.
    //---------------------------------------------
    override fun log(priority: Int, tag: String?, message: String, throwable: Throwable?) {
        val wrapper = SteamclogThrowableWrapper.from(throwable)
        val originalThrowable = wrapper?.originalThrowable

        val fullMessage = generateFullLogMessage(
            priority,
            createCustomStackElementTag(),
            includeTimestamp = true,
            includeEmoji = false,
            throwable,
            message
        )

        printLogToExternalFile(fullMessage)
        originalThrowable?.let {
            printLogToExternalFile(Log.getStackTraceString(it))
        }
    }

    //---------------------------------------------
    // Support to write logs out to External HTML file.
    //---------------------------------------------
    /**
     * [EMOJI?] [Date/Time UTC?] [App ID[Process ID?: Thread ID?]] ...
     * [Log Level] [Thread name] [(FileName.ext:line number): functionName()] > ...
     * [The actual log message], JSON: [JSON Object if applicable]
     */
    private fun printLogToExternalFile(message: String) {
        try {
            getExternalFile()?.let { file -> file.appendText("$message\r\n") }
        } catch (e: Exception) {
            logToConsole("HTMLFileTree failed to write into file: $e")
        }
    }

    private fun getExternalLogDirectory(): File? {
        val logDirectory = File(SteamcLog.config.fileWritePath, "logs")
        logDirectory.mkdirs()
        return logDirectory
    }

    private fun getExternalFile(): File? {
        val expiryMs = SteamcLog.config.autoRotateConfig.fileRotationSeconds * 1000 // defaults to 10 minutes

        if (isCachedLogFileValid(expiryMs)) {
            return cachedCurrentLogFile
        }

        val currentFile = findAvailableLogFile(expiryMs)
        updateCachedLogFile(currentFile)
        return currentFile
    }

    private fun isCachedLogFileValid(expiryMs: Long): Boolean {
        // if you have a cached log file and you checked it within the expiry window
        val now = Date().time
        val cachedCurrentLogFile = cachedCurrentLogFile
        val currentLogFileCachedTime = currentLogFileCachedTime
        return cachedCurrentLogFile != null &&
                currentLogFileCachedTime != null &&
                now - currentLogFileCachedTime < expiryMs
    }

    private fun updateCachedLogFile(file: File?) {
        cachedCurrentLogFile = file
        currentLogFileCachedTime = Date().time
    }

    private fun findAvailableLogFile(expiryMs: Long): File? {
        val now = Date().time
        val possibleLogFiles = rotatingIndexes.map { index ->
            val filename = "${fileNamePrefix}_${index}.${fileExt}"
            File(getExternalLogDirectory(), filename)
        }

        return try {
            // attempt to find an empty file or one within the expiry window
            var currentFile = possibleLogFiles.firstOrNull { file ->
                val isExpired = (now - file.lastModified() > expiryMs)
                if (!file.exists()) true else !isExpired
            }
            // if there are no available files, clear the oldest one and use it
            if (currentFile == null) {
                currentFile = possibleLogFiles.minByOrNull { it.lastModified() }
                currentFile?.writeText("")
            }
            currentFile
        } catch (e: Exception) {
            // Do not call Timber here, or will will infinitely loop
            logToConsole("HTMLFileTree failed to getExternalFile: $e")
            null
        }
    }

    private fun removeOldLogFiles() {
        val deleteThese = ArrayList<File>()
        val expiryMs = SteamcLog.config.keepLogsForDays * 86400000 // (86400000 ms per day)

        getExternalLogDirectory()?.listFiles()?.forEach { file ->
            val now = Date().time
            if (file.lastModified() < (now - expiryMs)) deleteThese.add(file)
        }

        deleteThese.forEach { file ->
            logToConsole("Deleting file ${file.name}")
            file.delete()
        }
    }

    internal suspend fun getLogFileContents(): String? {
        removeOldLogFiles()
        val logBuilder = StringBuilder()
        getExternalLogDirectory()?.listFiles()?.sortedBy { it.lastModified() }?.forEach { file ->
            try {
                logToConsole("Reading file ${file.name}")
                // This method is not recommended on huge files. It has an internal limitation of 2 GB file size.
                // todo, if we end up with super large logs we will have to read differently.
                logBuilder.append(file.readText() )
            } catch (e: Exception) {
                // Do not call Timber here, or will will infinitely loop
                logToConsole("getLogFileContents failed to read file: $e")
            }
        }

        return logBuilder.toString()
    }

    internal fun deleteLogFile() {
        getExternalFile()?.delete()
    }
}

//-----------------------------------------------------------------------------
// Extensions / Helpers
//-----------------------------------------------------------------------------
/**
 * Determines if the log (at given android.util.Log priority) should be logged given the
 * current tree logging level.
 */
internal fun Timber.Tree.isLoggable(treeLevel: LogLevel, logPriority: Int): Boolean {
    return (treeLevel != LogLevel.None) && (logPriority >= treeLevel.javaLevel)
}

/**
 * IMPORTANT: Must be called from a Destination's "override fun log" method for the
 * modified stacktrace to be calculated correctly.
 *
 * This method helps us get around that fact that because we are wrapping Timber calls, the
 * stacktrace information associated with the location of the report is relative to the Steamclog
 * codebase, and not the location where actual sclog method was invoked.
 *
 * Since Timber's createStackElementTag is made unusable since getTag is final (and gives
 * us the incorrect stacktrace location), this method attempts to generate the desired stacktrace
 * by creating a dummy Throwable and returning a modified version of its stacktrace relative to
 * the number of items in the stack due to Steamclog functionality.
 *
 * This is based on how Timber generates the stacktrace location for itself normally.
 */
private fun createCustomStackElementTag(): String {
    val SC_CALL_STACK_INDEX = 9 // Need to go back X in the call stack to get to the actual calling method.

    // ---- Taken directly from Timber ----
    // DO NOT switch this to Thread.getCurrentThread().getStackTrace(). The test will pass
    // because Robolectric runs them on the JVM but on Android the elements are different.
    val stackTrace = Throwable().stackTrace
    check(stackTrace.size > SC_CALL_STACK_INDEX) { "Synthetic stacktrace didn't have enough elements: are you using proguard?" }
    // ------------------------------------
    val element = stackTrace[SC_CALL_STACK_INDEX]
    val beforeCutoff = stackTrace[SC_CALL_STACK_INDEX - 1]
    val steamclogFileName = "Steamclog.kt"
    val internalLog = element.fileName == steamclogFileName
            && beforeCutoff.methodName == "logInternal"

    // Since unit testing is hard to do currently, add one more test on Debug builds that
    // attempts to determine if the stack index is pointing to the correct location.
    if (SteamcLog.config.isDebug && !internalLog) {
        check(beforeCutoff.fileName == steamclogFileName)
            { "createCustomStackElementTag failed: Element before cutoff no longer correct" }
        check(element.fileName != steamclogFileName) {
            { "createCustomStackElementTag failed: Element after cutoff no longer correct" }
        }
    }

    return "(${element.fileName}:${element.lineNumber}):${element.methodName}"
}

/**
 * Returns based on given parameters:
 * - [emoji][Message][: Extra data]
 */
private fun generateSimpleLogMessage(priority: Int,
                                     includeEmoji: Boolean,
                                     throwable: Throwable?,
                                     defaultMessage: String): String {

    val emoji = LogLevel.getLogLevel(priority)?.emoji
    val emojiStr = if (includeEmoji && emoji != null) { "$emoji " } else { "" }

    val wrapper = SteamclogThrowableWrapper.from(throwable)
    val extraData = wrapper?.extraData?.let { ": $it" } ?: run { "" }
    val originalMessage = wrapper?.originalMessage ?: defaultMessage
    return "$emojiStr$originalMessage$extraData"
}

/**
 * Returns based on given parameters:
 * - [timestamp][package name][processId:threadId] [priority] [stackTag] > [emoji][Message][: Extra data]
 */
private fun generateFullLogMessage(priority: Int,
                                   stackTag: String,
                                   includeTimestamp: Boolean,
                                   includeEmoji: Boolean,
                                   throwable: Throwable?,
                                   defaultMessage: String): String {
    val logTimestampFormat = "yyyy-MM-dd'.'HH:mm:ss.SSS"
    val logTimeStamp = if (includeTimestamp) {
        "${SimpleDateFormat(logTimestampFormat, Locale.US).format(Date())} "
    } else {
        ""
    }

    val fullMessage = generateSimpleLogMessage(priority, includeEmoji, throwable, defaultMessage)
    return logTimeStamp +
            BuildConfig.LIBRARY_PACKAGE_NAME +
            "[${android.os.Process.myPid()}:${Thread.currentThread().name}] " +
            "[$priority] [$stackTag] > $fullMessage"
}

/**
 * Used mostly for Steamclog to report message using Log (not Timber), since using
 * Timber may lead to infinite calls in the library.
 */
@SuppressLint("LogNotTimber")
internal fun logToConsole(message: String) {
    Log.v("steamclog", message)
}