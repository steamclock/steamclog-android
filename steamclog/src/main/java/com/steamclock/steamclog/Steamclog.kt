@file:Suppress("unused")

package com.steamclock.steamclog

import android.os.Process
import io.sentry.Sentry
import io.sentry.protocol.User
import org.jetbrains.annotations.NonNls
import timber.log.Timber
import java.io.File
import kotlin.system.exitProcess

/**
 * Steamclog
 *
 * Created by shayla on 2020-01-23
 *
 * A wrapper around the Timber logging library, giving us more control over what is logged and when.
 */

typealias clog = SteamcLog
@Suppress("SpellCheckingInspection")
object SteamcLog {

    //---------------------------------------------
    // Privates
    //---------------------------------------------
    // Below the 5 second ANR threshold, since fatal may be called on the main thread.
    private const val FATAL_FLUSH_TIMEOUT_MS = 2000L
    // Doc: docs/evergreen/log-files.md — flush log lines before the process ends (#151).
    // Together with FATAL_FLUSH_TIMEOUT_MS this stays below the ANR threshold.
    private const val FATAL_FILE_FLUSH_TIMEOUT_MS = 1000L
    // #151: A user report may be logged on the main thread; listing the files to attach must not
    // wait long enough to cause an ANR.
    private const val ATTACH_LOG_FILES_TIMEOUT_MS = 1000L
    private var flushOnCrashInstalled = false
    private const val FATAL_EXIT_CODE = 10

    @Suppress("JoinDeclarationAndAssignment")
    private var customDebugTree: ConsoleDestination
    private var sentryTree: SentryDestination
    private var externalLogFileTree: ExternalLogFileDestination

    //---------------------------------------------
    // Public properties
    //---------------------------------------------
    lateinit var config: Config
        private set

    /**
     * False until [initWith] is called. Every log before that is dropped (#169): without a
     * [Config] the library cannot choose a log level or redaction setting.
     */
    internal val isInitialized: Boolean
        get() = ::config.isInitialized

    init {
        // By default plant all trees; setting their level to LogLevel.None will effectively
        // disable that tree, but we do not uproot it.

        customDebugTree = ConsoleDestination()
        updateTree(customDebugTree, true)

        sentryTree = SentryDestination()
        updateTree(sentryTree, true)

        externalLogFileTree = ExternalLogFileDestination()
        // Don't plant yet; fileWritePath required before we can start writing to ExternalLogFileDestination
    }

    fun initWith(config: Config) {
        this.config = config
        this.config.fileWritePath?.let {
            updateTree(externalLogFileTree, true)
            externalLogFileTree.removeOldLogFiles()
            installFlushOnCrash()
        } ?: run {
            logInternal(LogLevel.Warn, "fileWritePath given was null; cannot log to external file")
        }
        logInternal(LogLevel.Info, "Steamclog initialized:\n$this")
    }

    //---------------------------------------------
    // Public Logging <level> calls
    //
    // Problems with wrapping Timber calls:
    // - Timber trace element containing line number and method points to THIS (Steamclog) file.
    //   The Destinations work around this by searching the call stack for the first frame outside
    //   SteamcLog and Timber (see createCustomStackElementTag in Destinations.kt).
    //
    // Note, using default parameter values (obj: Any? = null) introduces one more call in the
    // call stack. This used to break the fixed-depth stack lookup; the frame search (#144) skips
    // any extra SteamcLog frames, so it no longer matters for the tag. The explicit versions of
    // each <level> method below without optional parameters are kept as they are, since they are
    // public API.
    //---------------------------------------------
    fun verbose(@NonNls message: String)                = logTimber(LogLevel.Verbose, message, null, null, null)
    fun verbose(@NonNls message: String, obj: Any)      = logTimber(LogLevel.Verbose, message, null, obj, null)

    fun debug(@NonNls message: String)                  = logTimber(LogLevel.Debug, message, null, null, null)
    fun debug(@NonNls message: String, obj: Any)        = logTimber(LogLevel.Debug, message, null, obj, null )

    fun info(@NonNls message: String)                   = logTimber(LogLevel.Info, message, null, null, null)
    fun info(@NonNls message: String, obj: Any)         = logTimber(LogLevel.Info, message, null, obj, null)

    fun warn(@NonNls message: String)                   = logTimber(LogLevel.Warn, message, null, null, null)
    fun warn(@NonNls message: String, obj: Any)         = logTimber(LogLevel.Warn, message, null, obj, null)

    fun error(@NonNls message: String)                  = logTimber(LogLevel.Error, message, null, null, ExtraInfoPurpose.Error)
    fun error(@NonNls message: String, throwable: Throwable?) = logTimber(LogLevel.Error, message, throwable, null, ExtraInfoPurpose.Error)
    fun error(@NonNls message: String, throwable: Throwable?, obj: Any?) = logTimber(LogLevel.Error, message, throwable, obj, ExtraInfoPurpose.Error)

    /**
     * Logs the message, reports it to Sentry as a fatal event, then closes the app. Never returns.
     */
    fun fatal(@NonNls message: String): Nothing = fatal(message, null, null)

    /**
     * Logs the message, reports it to Sentry as a fatal event, then closes the app. Never returns.
     */
    fun fatal(@NonNls message: String, throwable: Throwable?): Nothing = fatal(message, throwable, null)

    /**
     * Logs the message, reports it to Sentry as a fatal event, then closes the app. Never returns.
     */
    fun fatal(@NonNls message: String, throwable: Throwable?, obj: Any?): Nothing {
        // #142: close in finally so an exception from an app callback (extraInfo, FilterOut)
        // cannot reach a caller that catches it and keeps running.
        try {
            logTimber(LogLevel.Fatal, message, throwable, obj, ExtraInfoPurpose.Fatal)
        } finally {
            closeApp()
        }
    }

    fun userReport(@NonNls message: String)             = logTimber(LogLevel.Error, message, null, null, ExtraInfoPurpose.UserReport)
    fun userReport(@NonNls message: String, throwable: Throwable?, obj: Any?) = logTimber(LogLevel.Error, message, throwable, obj, ExtraInfoPurpose.UserReport)

    /**
     *  Since Timber logging allows a Thowable to be passed to its trees, we wrap all data in
     *  a SteamclogThrowableWrapper, and let the Destinations handle the data components accordingly.
     */
    private fun logTimber(
        logLevel: LogLevel,
        @NonNls message: String,
        throwable: Throwable?,
        obj: Any?,
        purpose: ExtraInfoPurpose?
    ) {
        if (!isInitialized) return
        val extraInfo = purpose?.let { config.extraInfo?.invoke(purpose) }
        val redactableObjectData =  obj?.getRedactedDescription()
        val attachLogFiles = purpose == ExtraInfoPurpose.UserReport && config.detailedLogsOnUserReports

        val wrapper = SteamclogThrowableWrapper(message, throwable, attachLogFiles, redactableObjectData, extraInfo)
        Timber.log(logLevel.javaLevel, wrapper)
    }

    //---------------------------------------------
    // Public util methods
    //---------------------------------------------
    /**
     * This should be a fairly obscure ID, not something that could be immediately traced to a
     * specific individual (ie. not an email address)
     */
    fun setUserId(id: String) {
        // Write it to the log
        logInternal(LogLevel.Info,"Setting user id: $id")

        // Add user id to all subsequent crash reports
        val user = User().apply {
            this.id = id
        }

        Sentry.setUser(user)
    }

    fun clearUserId() {
        Sentry.setUser(null)
    }
    
//    fun setCustomKey(key: String, value: String) {
//        // todo #63: Set custom keys on Sentry reports?
//    }

    /**
     * Log files may be spread out across multiple files; this method will join
     * all log files into a single String.
     *
     * Blocks until every line logged before the call is written and every file is read, so
     * avoid calling it on the main thread.
     */
    fun getFullLogContents(): String? {
        return externalLogFileTree.getLogFileContents()
    }

    /**
     * Returns a list of ALL log files;
     * Blocks until every line logged before the call is written.
     */
    fun getAllLogFiles(logSort: LogSort): List<File>? {
        return externalLogFileTree.getLogFiles(logSort)
    }

    /**
     * The newest log files, for a user report's attachments. Unlike [getAllLogFiles], waits at
     * most ATTACH_LOG_FILES_TIMEOUT_MS and never throws, since it runs inside a log call (#144).
     * Returns null if the files could not be listed in time.
     */
    internal fun getLogFilesForAttachment(logSort: LogSort): List<File>? {
        return externalLogFileTree.getLogFilesOrNull(logSort, ATTACH_LOG_FILES_TIMEOUT_MS)
    }

    /**
     * Deletes log files older than [Config.keepLogsForDays]. Does nothing without a fileWritePath.
     */
    internal fun removeOldLogFiles() {
        if (config.fileWritePath == null) return
        externalLogFileTree.removeOldLogFiles()
    }

    fun addCustomTree(tree: Timber.Tree) {
        Timber.plant(tree)
    }

    //---------------------------------------------
    // Private methods
    //---------------------------------------------
    /**
     * Allows the Steamclog library to log info messages.
     * The Destinations tag each log with the first stack frame outside SteamcLog and Timber, so a
     * log made here is tagged with the app code that called into SteamcLog (for example, the
     * initWith call site), not with this file. Because of that search, calling the
     * info/debug/verbose methods directly from inside the library no longer breaks the tag
     * either (#144).
     */
    private fun logInternal(priority: LogLevel, message: String) {
        logTimber(priority, message, null, null, null)
    }

    /**
     * Ends the process after a fatal log (#142). Kills the process instead of throwing, so that
     * Sentry does not also capture the crash as a second event, and so a caller cannot catch it.
     * The log file and Sentry are flushed first so the fatal line is written and the fatal event
     * is sent before the process ends.
     */
    private fun closeApp(): Nothing {
        externalLogFileTree.flush(FATAL_FILE_FLUSH_TIMEOUT_MS)
        Sentry.flush(FATAL_FLUSH_TIMEOUT_MS)
        Process.killProcess(Process.myPid())
        // killProcess does not return in practice; exitProcess gives the compiler its Nothing.
        exitProcess(FATAL_EXIT_CODE)
    }

    /**
     * #151: Log lines are written on a background thread, so an uncaught exception could end the
     * process before the last lines reach the file. Wraps the current default handler (Sentry's,
     * when Sentry is set up) so the file is flushed first, and the replaced handler still runs.
     * Installed once, since initWith may be called again (#168).
     * Doc: docs/evergreen/log-files.md — flush before the process ends, with a timeout.
     */
    private fun installFlushOnCrash() {
        if (flushOnCrashInstalled) return
        flushOnCrashInstalled = true
        Thread.setDefaultUncaughtExceptionHandler(
            flushingUncaughtExceptionHandler(externalLogFileTree, Thread.getDefaultUncaughtExceptionHandler())
        )
    }

    /**
     * Returns a handler that flushes [fileTree], then passes the exception to [previous].
     * The flush waits at most FATAL_FILE_FLUSH_TIMEOUT_MS, so a stuck disk cannot stop the crash
     * from reaching Sentry. Internal so tests can build one without replacing the process-wide
     * handler.
     */
    internal fun flushingUncaughtExceptionHandler(
        fileTree: ExternalLogFileDestination,
        previous: Thread.UncaughtExceptionHandler?
    ) = Thread.UncaughtExceptionHandler { thread, throwable ->
        // finally: flush catches Exception but not Error. If it throws one (for example an
        // OutOfMemoryError during an out-of-memory crash), Sentry and Android must still run.
        try {
            fileTree.flush(FATAL_FILE_FLUSH_TIMEOUT_MS)
        } finally {
            previous?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Plants or uproots a tree accordingly.
     */
    private fun updateTree(tree: Timber.Tree, enabled: Boolean) {
        try {
            if (enabled) {
                // #168: Timber.plant does not skip a tree that is already planted, so a second
                // initWith call would write every log line to the file twice.
                if (tree !in Timber.forest()) Timber.plant(tree)
            } else {
                Timber.uproot(tree)
            }
        } catch (e: Exception) {
            // Tree may not be planted, catch exception.
        }
    }

    override fun toString(): String {
        return config.toString()
    }
}