@file:Suppress("unused")

package com.steamclock.steamclog

import io.sentry.Sentry
import io.sentry.protocol.User
import org.jetbrains.annotations.NonNls
import timber.log.Timber
import java.io.File

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
    @Suppress("JoinDeclarationAndAssignment")
    private var customDebugTree: ConsoleDestination
    private var sentryTree: SentryDestination
    private var externalLogFileTree: ExternalLogFileDestination

    //---------------------------------------------
    // Public properties
    //---------------------------------------------
    lateinit var config: Config
        private set

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
    //
    // Note, using default parameter values (obj: Any? = null) appears to introduce one more call in the
    // call stack that messes up how we are generating our PriorityEnabledDebugTree's stack element.
    // To get around this for now we explicit versions of each <level> method below without optional
    // parameters.
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
    fun error(@NonNls message: String, throwable: Throwable?, obj: Any?) = logTimber(LogLevel.Error, message, throwable, obj, ExtraInfoPurpose.Error)

    fun fatal(@NonNls message: String)                  = logTimber(LogLevel.Fatal, message, null, null, ExtraInfoPurpose.Fatal)
    fun fatal(@NonNls message: String, throwable: Throwable?, obj: Any?) = logTimber(LogLevel.Fatal, message, throwable, obj, ExtraInfoPurpose.Fatal)

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
        Sentry.configureScope { scope ->
            scope.user = null
        }
    }
    
//    fun setCustomKey(key: String, value: String) {
//        // todo #63: Set custom keys on Sentry reports?
//    }

    /**
     * Log files may be spread out across multiple files; this method will join
     * all log files into a single String.
     */
    fun getFullLogContents(): String? {
        return externalLogFileTree.getLogFileContents()
    }

    /**
     * Returns a list of ALL log files;
     */
    fun getAllLogFiles(logSort: LogSort): List<File>? {
        return externalLogFileTree.getLogFiles(logSort)
    }

    fun addCustomTree(tree: Timber.Tree) {
        Timber.plant(tree)
    }

    //---------------------------------------------
    // Private methods
    //---------------------------------------------
    private fun addObjToMessage(@NonNls message: String, obj: Any?): String {
        return when(obj) {
            null -> message
            is Throwable -> "$message ${obj.message?.let { throwMsg -> " : $throwMsg" }}"
            else -> "$message : ${obj.getRedactedDescription()}"
        }
    }

    /**
     * Allows the Steamclog library to log info messages.
     * Due to stacktrace manipultions being done in the Destinations, we should not call
     * the info/debug/verbose calls directly.
     */
    private fun logInternal(priority: LogLevel, message: String) {
        logTimber(priority, message, null, null, null)
    }

    /**
     * Plants or uproots a tree accordingly.
     */
    private fun updateTree(tree: Timber.Tree, enabled: Boolean) {
        try {
            if (enabled) {
                Timber.plant(tree)
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