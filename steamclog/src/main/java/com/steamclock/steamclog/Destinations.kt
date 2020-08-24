package com.steamclock.steamclog

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.sentry.core.Sentry
//import io.sentry.core.Sentry
import timber.log.Timber
import java.io.File
import java.lang.StringBuilder
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
 * CrashlyticsDestination
 */
internal class CrashlyticsDestination : Timber.Tree() {
    /**
     * Creates a placeholder exception that can be used to report non-fatals that do not have an
     * underlying Throwable error associated with them. This class uses an abbreviatedStackTrace
     * so that the non-fatal will be reported at the line of code that called the steamclog.error
     * method.
     */
    class NonFatalException(message: String, private val abbreviatedStackTrace: Array<StackTraceElement>): Exception(message) {
        override fun getStackTrace(): Array<StackTraceElement> { return abbreviatedStackTrace }
        companion object {
            fun with(message: String): NonFatalException {
                val stackTrace = Thread.currentThread().stackTrace
                val numToRemove = 10 // Move down the stack past Timber and Steamclog calls.
                val abbreviatedStackTrace = stackTrace.takeLast(stackTrace.size - numToRemove).toTypedArray()
                return NonFatalException(message, abbreviatedStackTrace)
            }
        }
    }

    override fun isLoggable(priority: Int): Boolean {
        return isLoggable(SteamcLog.config.logLevel.crashlytics, priority)
    }

    override fun log(priority: Int, tag: String?, message: String, throwable: Throwable?) {
        FirebaseCrashlytics.getInstance().apply {
            log(message)
            if (priority == Log.ERROR) {
                // If no throwable associated with the error log, create a generic NonFatalException.
                val throwMe = throwable ?: NonFatalException.with(message)
                recordException(throwMe)
            }
        }
    }
}

/**
 * SentryDestination
 */
internal class SentryDestination : Timber.Tree() {
    override fun isLoggable(priority: Int): Boolean {
        return isLoggable(SteamcLog.config.logLevel.sentry, priority)
    }

    /**
     * From Sentry docs: By default, the last 100 breadcrumbs are kept and attached to next event.
     */
    override fun log(priority: Int, tag: String?, message: String, throwable: Throwable?) {
        when {
            priority == Log.ERROR && throwable != null -> {
                // If given a throwable, add message as breadcrumb, and log exception
                Sentry.addBreadcrumb(message)
                Sentry.captureException(throwable)
            }
            priority == Log.ERROR -> {
                // If no throwable given, log error as message
                Sentry.captureMessage(message)
            }
            else -> {
                // Not an error, add message as breadcrumb
                Sentry.addBreadcrumb(message)
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
        val emoji = LogLevel.getLogLevel(priority)?.emoji
        val prettyMessage = if (emoji == null) message else "$emoji $message"
        super.log(priority, createCustomStackElementTag(), prettyMessage, throwable)
    }
}

/**
 * ExternalLogFileDestination
 * DebugTree gives us access to override createStackElementTag
 */
internal class ExternalLogFileDestination : Timber.DebugTree() {
    private var fileNamePrefix: String = "sclog"
    private var fileNameTimestamp = "yyyy_MM_dd"
    private var logTimestampFormat = "yyyy-MM-dd'.'HH:mm:ss.SSS"
    private var fileExt = "txt"

    override fun isLoggable(priority: Int): Boolean {
        return isLoggable(SteamcLog.config.logLevel.file, priority)
    }

    //---------------------------------------------
    // Reformats console output to include file and line number to log.
    //---------------------------------------------
    override fun createStackElementTag(element: StackTraceElement): String? {
        return "(${element.fileName}:${element.lineNumber}):${element.methodName}"
    }

    //---------------------------------------------
    // Allows us to print out to an external file if desired.
    //---------------------------------------------
    override fun log(priority: Int, tag: String?, message: String, throwable: Throwable?) {
        printLogToExternalFile(priority, tag, message)
    }

    //---------------------------------------------
    // Support to write logs out to External HTML file.
    //---------------------------------------------
    private fun printLogToExternalFile(priority: Int, tag: String?, message: String) {
        try {
            val date = Date()
            val logTimeStamp = SimpleDateFormat(logTimestampFormat, Locale.US).format(date)
            val appId = BuildConfig.LIBRARY_PACKAGE_NAME
            val processId = android.os.Process.myPid()
            val threadName = Thread.currentThread().name
            val logStr = "$logTimeStamp $appId[$processId:$threadName] [$priority] [$tag] > $message"

            // If file created or exists save logs
            getExternalFile()?.let { file -> file.appendText(logStr) }
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
 * Used mostly for Steamclog to report message using Log (not Timber), since using
 * Timber may lead to infinite calls in the library.
 */
internal fun logToConsole(message: String) {
    Log.v("steamclog", message)
}

/**
 * Timber is using a specific call stack index to correctly generate the stack element to be used
 * in the createStackElementTag method, which is included in a final method we have no control over.
 * Because we are wrapping Timber calls in Steamclog,alll of our
 * that stack call index point to our library, instead of the calling method.
 *
 * getStackTraceElement uses a call stack index relative to our library, BUT because we cannot override
 * Timber.getTag, we cannot get access to the stack trace element during our log step. As such,
 * we need to use this method to allow us to get the correct stack trace element associated with the
 * actual call to our Steamclog.
 */
private fun getStackTraceElement(): StackTraceElement {

    val SC_CALL_STACK_INDEX = 10 // Need to go back X in the call stack to get to the actual calling method.

    // ---- Taken directly from Timber ----
    // DO NOT switch this to Thread.getCurrentThread().getStackTrace(). The test will pass
    // because Robolectric runs them on the JVM but on Android the elements are different.
    val stackTrace = Throwable().stackTrace
    check(stackTrace.size > SC_CALL_STACK_INDEX) { "Synthetic stacktrace didn't have enough elements: are you using proguard?" }
    // ------------------------------------

    return stackTrace[SC_CALL_STACK_INDEX]
}

/**
 * Since Timber's createStackElementTag is made unusable to us since getTag is final, I have created
 * createCustomStackElementTag that makes use of our custom call stack index to give us better filename
 * and linenumber reporting.
 */
private fun createCustomStackElementTag(): String {
    val element = getStackTraceElement()
    return "(${element.fileName}:${element.lineNumber}):${element.methodName}"
}