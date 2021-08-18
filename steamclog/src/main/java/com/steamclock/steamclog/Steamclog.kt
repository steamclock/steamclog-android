@file:Suppress("unused")

package com.steamclock.steamclog

import io.sentry.Sentry
import io.sentry.protocol.User
import org.jetbrains.annotations.NonNls
import timber.log.Timber
import java.io.File
import kotlin.reflect.KClass

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
    var config: Config = Config()
        private set

    /**
     * Can be overridden by the application to allow Throwables to be filtered out before
     * being sent as errors to the crash reporting destination.
     */
    var throwableFilter: ThrowableFilter = ThrowableFilter { false } // By default filter nothing

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

    fun initWith(isDebug: Boolean,
                 fileWritePath: File? = null) {
        fileWritePath?.let {
            this.config = Config(isDebug, fileWritePath)
            updateTree(externalLogFileTree, true)
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
    fun verbose(@NonNls message: String)                = logTimber(LogLevel.Verbose, message, null, null)
    fun verbose(@NonNls message: String, obj: Any)      = logTimber(LogLevel.Verbose, message, null, obj)

    fun debug(@NonNls message: String)                  = logTimber(LogLevel.Debug, message, null, null)
    fun debug(@NonNls message: String, obj: Any)        = logTimber(LogLevel.Debug, message, null, obj)

    fun info(@NonNls message: String)                   = logTimber(LogLevel.Info, message, null, null)
    fun info(@NonNls message: String, obj: Any)         = logTimber(LogLevel.Info, message, null, obj)

    fun warn(@NonNls message: String)                   = logTimber(LogLevel.Warn, message, null, null)
    fun warn(@NonNls message: String, obj: Any)         = logTimber(LogLevel.Warn, message, null, obj)

    fun error(@NonNls message: String)                  = logTimber(LogLevel.Error, message, null, null)
    fun error(@NonNls message: String, throwable: Throwable?, obj: Any?) = logTimber(LogLevel.Error, message, throwable, obj)

    fun fatal(@NonNls message: String)                  = logTimber(LogLevel.Fatal, message, null, null)
    fun fatal(@NonNls message: String, throwable: Throwable?, obj: Any?) = logTimber(LogLevel.Fatal, message, throwable, obj)

    /**
     *  Since Timber logging allows a Thowable to be passed to its trees, we wrap all data in
     *  a SteamclogThrowableWrapper, and let the Destinations handle the data components accordingly.
     */
    private fun logTimber(logLevel: LogLevel, @NonNls message: String, throwable: Throwable?, obj: Any?) {
        val extraData =  obj?.getRedactedDescription()
        val wrapper = SteamclogThrowableWrapper(message, throwable, extraData)
        Timber.log(logLevel.javaLevel, wrapper)
    }

    // todo #64 Re-implement track method; was removed when we ported over to using Sentry
//    fun track(@NonNls id: String, data: Map<String, Any?>) {
//        if (!config.logLevel.analyticsEnabled) {
//            logInternal(LogLevel.Info, "Anayltics not enabled ($id)")
//            return
//        }
//
//        if (config.firebaseAnalytics == null) {
//            logInternal(LogLevel.Info, "Firebase analytics instance not set ($id)")
//            return
//        }
//
//        val bundle = Bundle()
//        data.forEach { (key, value) ->
//            when (value) {
//                is Redactable -> {
//                    bundle.putString(key, value.getRedactedDescription())
//                }
//                is Serializable -> {
//                    bundle.putSerializable(key, value)
//                }
//                is Parcelable -> {
//                    bundle.putParcelable(key, value)
//                }
//                else -> {
//                    warn("Failed to encode $value to bundle, must be either parcelable, serializable or redactable")
//                    return
//                }
//            }
//        }
//
//        // Obtain the FirebaseAnalytics instance from the config.
//        config.firebaseAnalytics?.apply { logEvent(id, bundle) }
//    }

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

    suspend fun getLogFileContents(): String? {
        return externalLogFileTree.getLogFileContents()
    }

    fun deleteLogFile() {
        return externalLogFileTree.deleteLogFile()
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
        logTimber(priority, message, null, null)
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