package com.steamclock.steamclog

import android.util.Log
import timber.log.Timber
import java.io.File
import java.lang.StringBuilder
import java.text.SimpleDateFormat
import java.util.*
import io.sentry.Sentry

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

        val breadcrumbMessage = generateSimpleLogMessage(
            priority,
            includeEmoji = false,
            wrapper,
            message
        )

        when {
            priority == Log.ERROR && originalThrowable != null -> {
                // If given an original throwable, capture it
                Sentry.addBreadcrumb(breadcrumbMessage, breadcrumbCategory)
                Sentry.captureException(originalThrowable)
            }
            priority == Log.ERROR -> {
                // If no throwable given, log error as message (no throwable)
                Sentry.addBreadcrumb(breadcrumbMessage, breadcrumbCategory)
                Sentry.captureMessage(originalMessage)
            }
            else -> {
                // Not an error, add breadcrumb only (which should include
                // both the message and any extra data.
                Sentry.addBreadcrumb(breadcrumbMessage, breadcrumbCategory)
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
    private var fileNameTimestamp = "yyyy_MM_dd"
    private var fileExt = "txt"

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
        val date = SimpleDateFormat(fileNameTimestamp, Locale.US).format(Date())
        val filename = "${fileNamePrefix}_${date}.${fileExt}"

        return try {
            File(getExternalLogDirectory(), filename)
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
        getExternalLogDirectory()?.listFiles()?.forEach { file ->
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
internal fun logToConsole(message: String) {
    Log.v("steamclog", message)
}