package com.example.lib

import org.jetbrains.annotations.NonNls
import timber.log.Timber

/**
 * Steamclog
 *
 * Created by shayla on 2020-01-23
 *
 * A wrapper around the Timber logging library, giving us more control over what is logged and when.
 */

typealias clog = Steamclog
object Steamclog {

    //---------------------------------------------
    // Privates
    //---------------------------------------------
    private var crashlyticsTree = CrashlyticsDestination()
    private var customDebugTree = ConsoleDestination()
    private var externalLogFileTree = ExternalLogFileDestination()

    //---------------------------------------------
    // Public properties
    //---------------------------------------------
    var config: Config = Config()

    init {
        // By default plant all trees; setting their level to LogLevel.None will effectively
        // disable that tree, but we do not uproot it.
        updateTree(customDebugTree, true)
        updateTree(crashlyticsTree, true)
        updateTree(externalLogFileTree, true)
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
    fun verbose(@NonNls message: String)                = logTimber(LogLevel.Verbose, message)
    fun verbose(@NonNls message: String, obj: Any)      = logTimber(LogLevel.Verbose, message, obj)

    fun debug(@NonNls message: String)                  = logTimber(LogLevel.Debug, message)
    fun debug(@NonNls message: String, obj: Any)        = logTimber(LogLevel.Debug, message, obj)

    fun info(@NonNls message: String)                   = logTimber(LogLevel.Info, message)
    fun info(@NonNls message: String, obj: Any)         = logTimber(LogLevel.Info, message, obj)

    fun warn(@NonNls message: String)                   = logTimber(LogLevel.Warn, message)
    fun warn(@NonNls message: String, obj: Any)         = logTimber(LogLevel.Warn, message, obj)

    fun error(@NonNls message: String)                  = logTimber(LogLevel.Error, message)
    fun error(@NonNls message: String, obj: Any)        = logTimber(LogLevel.Error, message, obj)
    fun error(@NonNls message: String, throwable: Throwable?, obj: Any?) = logTimber(LogLevel.Error, message, throwable, obj)

    fun fatal(@NonNls message: String)                  = logTimber(LogLevel.Fatal, message)
    fun fatal(@NonNls message: String, obj: Any)        = logTimber(LogLevel.Fatal, message, obj)
    fun fatal(@NonNls message: String, throwable: Throwable?, obj: Any?) = logTimber(LogLevel.Fatal, message, throwable, obj)

    // Mapping onto the corresponding Timber calls.
    private fun logTimber(logLevel: LogLevel, @NonNls message: String) = Timber.log(logLevel.javaLevel, message)
    private fun logTimber(logLevel: LogLevel, @NonNls message: String, throwable: Throwable?, obj: Any?) = Timber.log(logLevel.javaLevel, throwable, addObjToMessage(message, obj))
    private fun logTimber(logLevel: LogLevel, @NonNls message: String, obj: Any?) {
        if (obj is Throwable) {
            Timber.log(logLevel.javaLevel, obj, message)
        } else {
            Timber.log(logLevel.javaLevel, addObjToMessage(message, obj))
        }
    }

    //---------------------------------------------
    // Public util methods
    //---------------------------------------------
    fun getLogFileContents(): String? {
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
        return if (obj == null) {
            message
        } else {
            "$message : ${obj.getRedactedDescription()}"
        }
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
        } catch(e: Exception) {
            // Tree may not be planted, catch exception.
        }
    }

    override fun toString(): String {
        return config.toString()
    }
}