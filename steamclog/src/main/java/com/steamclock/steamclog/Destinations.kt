package com.steamclock.steamclog

import android.annotation.SuppressLint
import android.util.Log
import io.sentry.*
import io.sentry.protocol.Message
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
 * SentryDestination == remote
 */
internal class SentryDestination : Timber.Tree() {

    companion object {
        const val breadcrumbCategory = "steamclog"
        const val attachNumLogFiles = 2
    }

    override fun isLoggable(priority: Int): Boolean {
        return isLoggable(SteamcLog.config.logLevel.remote, priority)
    }

    /**
     * From Sentry docs: By default, the last 100 breadcrumbs are kept and attached to next event.
     */
    override fun log(priority: Int, tag: String?, message: String, throwable: Throwable?) {
        val wrapper = SteamclogThrowableWrapper.from(throwable)
        val originalMessage = wrapper?.originalMessage ?: message
        val originalThrowable = wrapper?.originalThrowable

        // Create a full log message and include as a breadcrumb to Sentry.
        val verboseLogMessage = generateSimpleLogMessage(priority, includeEmoji = false, wrapper, originalMessage)
        Sentry.addBreadcrumb(createSentryBreadcrumbFor(priority, verboseLogMessage))

        // Log error report if desired.
        if (priority == Log.ERROR) {
            if (originalThrowable != null) {
                // Check to see if we want to block this Throwable from being logged as an Error.
                if (SteamcLog.config.filtering.shouldBlock(originalThrowable)) {
                    Sentry.addBreadcrumb(
                        "${originalThrowable::class.simpleName} on blocked list, and has " +
                                "been blocked from being captured as an exception: " +
                                "${originalThrowable.message}", breadcrumbCategory
                    )
                    return
                }
                // Log stack trace as a breadcrumb (this logged as an INFO breadcrumb by default)
                Sentry.addBreadcrumb(originalThrowable.stackTraceToString(), breadcrumbCategory)
            }

            // Always use the message as our error report, as this gives us way more contextual
            // info about the problem than the name of the Exception.
            val sentryEvent = SentryEvent(originalThrowable).apply {
                level = SentryLevel.ERROR
                setMessage(Message().apply {
                    setMessage(originalMessage)
                    formatted = originalMessage
                })
                wrapper?.extraInfo?.let { extras = it }
            }

            // Attach log files if desired
            val hintWithAttachments = when (wrapper?.attachLogFiles) {
                true -> {
                    val attachments = mutableListOf<Attachment>()
                    // Sort by descending modified time so that we get the "latest" log files
                    SteamcLog.getAllLogFiles(LogSort.LastModifiedDesc)
                        ?.take(attachNumLogFiles)
                        ?.forEach { file -> attachments += Attachment(file.absolutePath) }

                    when (attachments.size) {
                        0 -> null
                        else -> Hint.withAttachments(attachments.toList())
                    }
                }
                else -> null
            }

            Sentry.captureEvent(sentryEvent, hintWithAttachments)
        }
    }

    /**
     * Takes the given priority level and creates a breadcrumb at a similar level. Mappings
     * aren't 1 to 1, but this should still give us better detail in our breadcrumb log.
     */
    private fun createSentryBreadcrumbFor(priority: Int, message: String): Breadcrumb {
       val breadcrumb =  when (priority) {
            Log.ASSERT -> Breadcrumb.error(message)
            Log.ERROR -> Breadcrumb.error(message)
            Log.WARN -> Breadcrumb.info(message)
            // Log.INFO is the default
            Log.DEBUG -> Breadcrumb.debug(message)
            Log.VERBOSE -> Breadcrumb.debug(message)
            else -> Breadcrumb.info(message)
        }
        breadcrumb.category = breadcrumbCategory
        return breadcrumb
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
 * ExternalLogFileDestination == Disk
 * DebugTree gives us access to override createStackElementTag
 */
internal class ExternalLogFileDestination : Timber.DebugTree() {

    companion object {
        // Number of log files kept before rotating
        private const val logFileRotations = 10
    }

    private var fileNamePrefix: String = "sclog"
    private var fileExt = "txt"
    private var cachedCurrentLogFile: File? = null
    // Since we cannot get a file's created time, we make note of the timestamp when the
    // logFile was originally marked as being "current".
    private var logFileCreatedTimeEstimate: Long? = null

    override fun isLoggable(priority: Int): Boolean {
        return isLoggable(SteamcLog.config.logLevel.disk, priority)
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

    /**
     * Will get the external file we are currently writing to.
     */
    private fun getExternalFile(): File? {
        val expiryMs = SteamcLog.config.autoRotateConfig.fileRotationSeconds * 1000 // defaults to 10 minutes

        if (isCachedLogFileValid(expiryMs)) {
            return cachedCurrentLogFile
        }

        // Find new available log file and update cached variables
        cachedCurrentLogFile = findAvailableLogFile(expiryMs)
        logFileCreatedTimeEstimate = cachedCurrentLogFile?.lastModified()
        return cachedCurrentLogFile
    }

    private fun isCachedLogFileValid(expiryMs: Long): Boolean {
        // if you have a cached log file and you checked it within the expiry window
        cachedCurrentLogFile ?: return false
        val logFileCreationTime = logFileCreatedTimeEstimate ?: return false
        return (Date().time - logFileCreationTime) < expiryMs
    }

    private fun findAvailableLogFile(expiryMs: Long): File? {
        val now = Date().time
        val possibleLogFiles = List(logFileRotations) { it }
            .map { index ->
                val filename = "${fileNamePrefix}_${index}.${fileExt}"
                File(getExternalLogDirectory(), filename)
            }
            .filter {
                // Small hack: Filter out current log file from available list.
                // It is possible that it's lastModified timestamp may cause the "expiry"
                // check below to incorrectly flag the file as still available.
                it.name != cachedCurrentLogFile?.name
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

            // If last modified time is null, write to the file to set it since we need it
            // when calculating an expiry time.
            if (currentFile?.lastModified() == null || currentFile?.lastModified() == 0L) {
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

    /**
     * Returns the list of log files written to the log directory, sorted by the desired method.
     */
    fun getLogFiles(sort: LogSort): List<File>? {
        return when (sort) {
            LogSort.LastModifiedAsc -> {
                getExternalLogDirectory()?.listFiles()?.sortedBy { it.lastModified() }
            }
            LogSort.LastModifiedDesc -> {
                getExternalLogDirectory()?.listFiles()?.sortedByDescending { it.lastModified() }
            }
        }
    }

    /**
     * Returns the content of ALL log files strung together to form a single String.
     * todo Given the size of the log files it may not be feasible to output this. This meathod
     *   was mostly used by the sample app to test out how the files are being split up.
     */
    internal fun getLogFileContents(): String {
        removeOldLogFiles()
        val logBuilder = StringBuilder()
        getLogFiles(LogSort.LastModifiedAsc)?.forEach { file ->
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
    /**
     * How many items we need to "go back" in the call stack to get to method that called our
     * steamclog logging method.
     *
     * NOTE: This number may change when libraries are updated, as those updates may affect the
     * state of the stack trace and how the stack is handled.
     */
    val SC_CALL_STACK_INDEX = 8

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
    //
    // NOTE: If these checks are failing then it's possible a library has been updated which may
    // have affected the depth of the call stack at this point, and as such the SC_CALL_STACK_INDEX
    // may need to be updated. Place a debug break here, check the stackTrace array and find which
    // index the actual logging call was made. This will most likely be the new
    // SC_CALL_STACK_INDEX value.
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
    val extraData = wrapper?.redactedObjectData?.let { ": $it" } ?: run { "" }
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