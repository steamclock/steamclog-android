package com.steamclock.steamclog

import android.annotation.SuppressLint
import android.util.Log
import io.sentry.*
import io.sentry.protocol.Message
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Destinations
 *
 * Created by shayla on 2020-01-23
 */

//-----------------------------------------------------------------------------
// The SteamcLog tree and its destinations
//
// SteamcLog plants one Timber tree, SteamclogTree. It prepares each log call once (the caller
// location, the transformed text) and hands the result to every destination whose level in
// Steamclog.config.logLevel accepts it. The destinations are never planted; ConsoleDestination
// extends DebugTree only for its Logcat output.
//-----------------------------------------------------------------------------
/** Where a prepared log call is written or sent. */
internal interface LogDestination {
    /** True when this destination takes logs at [priority] with the current config. */
    fun accepts(priority: Int): Boolean
    /** True when [write] uses [LogEvent.callerTag], which costs a stack walk to compute. */
    val needsCallerTag: Boolean
    fun write(event: LogEvent)
}

/**
 * The one tree SteamcLog plants (#187). Timber calls it for `clog.*` calls and direct Timber calls
 * alike, so each log call is prepared once and [Config.messageTransform] runs once per piece of
 * text, whatever the number of destinations.
 */
internal class SteamclogTree(private val destinations: List<LogDestination>) : Timber.Tree() {

    override fun isLoggable(priority: Int): Boolean {
        // #169: drops logs made before initWith, direct Timber logs included.
        return SteamcLog.isInitialized && destinations.any { it.accepts(priority) }
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val targets = destinations.filter { it.accepts(priority) }
        // Computed here, not in a destination or a lazy property: the caller search skips only
        // SteamcLog and Timber frames, so it must run from SteamcLog code.
        val callerTag = if (targets.any { it.needsCallerTag }) createCustomStackElementTag(tag) else null
        val event = LogEvent(priority, message, t, callerTag)
        for (destination in targets) destination.write(event)
    }
}

/**
 * One log call, prepared once for every destination (#187). The transformed text is computed in
 * the constructor, on the thread that logged.
 */
internal class LogEvent(
    val priority: Int,
    timberMessage: String,
    throwable: Throwable?,
    /** The caller location, or null when no destination that takes this log needs it. */
    val callerTag: String?
) {
    private val wrapper = SteamclogThrowableWrapper.from(throwable, timberMessage)
    val originalThrowable: Throwable? = wrapper?.originalThrowable
    val attachLogFiles: Boolean = wrapper?.attachLogFiles == true
    val extraInfo: Map<String, Any>? = wrapper?.extraInfo
    private val originalMessage = wrapper?.originalMessage ?: timberMessage
    private val extraData = wrapper?.redactedObjectData?.let { ": $it" } ?: ""
    private val transformSet = SteamcLog.isInitialized && SteamcLog.config.messageTransform != null

    /** [Message][: Extra data], transformed. */
    val text: String = transformLogText("$originalMessage$extraData")

    /**
     * The Sentry event message: the message without the extra data, transformed. Only error and
     * fatal logs become Sentry events, so for other levels this is [text] and costs no transform.
     */
    val eventMessage: String =
        if (extraData.isEmpty() || priority < Log.ERROR) text else transformLogText(originalMessage)

    /**
     * Each throwable printed in [originalThrowable]'s stack trace, with its (header line,
     * transformed header line). A header is the throwable's toString(), the "Class: message" line
     * the stack trace prints, so a custom toString() is covered too.
     */
    private val transformedHeaders: Map<Throwable, Pair<String, String>> =
        if (originalThrowable == null || !transformSet) emptyMap()
        else transformHeaders(printedThrowables(originalThrowable), emptyMap())

    /**
     * Returns [stackTrace], the printed stack trace of [originalThrowable], with each header line
     * replaced by its transformed form. The "at ..." frames are left as they are. Safe to call on
     * any thread; it does not change this event.
     */
    fun transformStackTrace(stackTrace: String): String {
        val throwable = originalThrowable ?: return stackTrace
        if (!transformSet) return stackTrace
        // #187: a cause or suppressed throwable can be added after the log call, for example when
        // use {} closes a resource after a log-and-rethrow and the file thread renders the trace
        // later. Only those are transformed here, on the thread that renders the trace.
        val headers = transformHeaders(printedThrowables(throwable), transformedHeaders)
        return replaceHeaders(stackTrace, headers.values)
    }
}

/**
 * Returns [known], plus the (header, transformed header) of each throwable in [printed] that
 * [known] does not have yet.
 */
private fun transformHeaders(
    printed: Collection<Throwable>,
    known: Map<Throwable, Pair<String, String>>
): Map<Throwable, Pair<String, String>> {
    val result = IdentityHashMap(known)
    for (throwable in printed) {
        if (throwable in result) continue
        val header = throwable.toString()
        result[throwable] = header to transformLogText(header)
    }
    return result
}

/**
 * Replaces each header in [stackTrace] with its transformed form, in one pass with the longest
 * header first (#187). A header that contains another (an outer exception whose message repeats
 * its cause, or two messages where one starts with the other) is replaced whole, and replaced text
 * is never matched again.
 */
private fun replaceHeaders(stackTrace: String, headers: Collection<Pair<String, String>>): String {
    val replacements = headers.filter { (header, transformed) -> header.isNotEmpty() && header != transformed }.toMap()
    if (replacements.isEmpty()) return stackTrace
    val pattern = replacements.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
    return Regex(pattern).replace(stackTrace) { replacements.getValue(it.value) }
}

/**
 * SentryDestination == remote
 */
internal class SentryDestination : LogDestination {

    companion object {
        const val breadcrumbCategory = "steamclog"
        const val attachNumLogFiles = 2
    }

    override val needsCallerTag = false

    override fun accepts(priority: Int): Boolean {
        return SteamcLog.isInitialized && isLoggable(SteamcLog.config.logLevel.remote, priority)
    }

    /**
     * From Sentry docs: By default, the last 100 breadcrumbs are kept and attached to next event.
     */
    override fun write(event: LogEvent) {
        val priority = event.priority
        val originalThrowable = event.originalThrowable

        // Create a full log message and include as a breadcrumb to Sentry.
        Sentry.addBreadcrumb(createSentryBreadcrumbFor(priority, event.text))

        // Only error and fatal logs become Sentry events. Every other level stays a breadcrumb (#142).
        val eventLevel = when (priority) {
            Log.ERROR -> SentryLevel.ERROR
            Log.ASSERT -> SentryLevel.FATAL
            else -> null
        }
        if (eventLevel != null) {
            if (originalThrowable != null) {
                // Check to see if we want to block this Throwable from being logged as an Error.
                if (SteamcLog.config.filtering.shouldBlock(originalThrowable)) {
                    Sentry.addBreadcrumb(
                        "${originalThrowable::class.simpleName} on blocked list, and has " +
                                "been blocked from being captured as an exception: " +
                                "${originalThrowable.message?.let { transformLogText(it) }}", breadcrumbCategory
                    )
                    return
                }
                // Log stack trace as a breadcrumb (this logged as an INFO breadcrumb by default)
                Sentry.addBreadcrumb(
                    event.transformStackTrace(originalThrowable.stackTraceToString()),
                    breadcrumbCategory
                )
            }

            // Always use the message as our error report, as this gives us way more contextual
            // info about the problem than the name of the Exception.
            val sentryEvent = SentryEvent(originalThrowable).apply {
                level = eventLevel
                setMessage(Message().apply {
                    setMessage(event.eventMessage)
                    formatted = event.eventMessage
                })
                event.extraInfo?.let { setExtras(it) }
            }

            // Attach log files if desired
            val hintWithAttachments = when (event.attachLogFiles) {
                true -> {
                    val attachments = mutableListOf<Attachment>()
                    // #147: Expire old files first so a report never attaches them.
                    SteamcLog.removeOldLogFiles()
                    // Sort by descending modified time so that we get the "latest" log files.
                    // #151: a timed, non-throwing read, since this runs inside a log call.
                    val logFiles = SteamcLog.getLogFilesForAttachment(LogSort.LastModifiedDesc)
                        ?.take(attachNumLogFiles) ?: emptyList()
                    for (file in logFiles) { attachments += Attachment(file.absolutePath) }

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
 * Extends DebugTree only for its Logcat output, which splits long messages; it is never planted.
 */
internal class ConsoleDestination: Timber.DebugTree(), LogDestination {

    override val needsCallerTag = true

    override fun accepts(priority: Int): Boolean {
        return SteamcLog.isInitialized && isLoggable(SteamcLog.config.logLevel.console, priority)
    }

    override fun write(event: LogEvent) {
        // Since we are relying on android.util.log formatting here,
        // do not call generateFormattedLogMessage
        super.log(event.priority, event.callerTag, generateConsoleLogMessage(event), event.originalThrowable)
    }
}
/**
 * ExternalLogFileDestination == Disk
 */
internal class ExternalLogFileDestination : LogDestination {

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

    // Doc: docs/evergreen/log-files.md — all log file access goes through this one thread (#151).
    // A single-thread executor runs queued tasks one at a time, in order: execute() queues without
    // waiting, submit() + get() waits for the result. Created on first use.
    private val fileExecutor: ExecutorService by lazy {
        Executors.newSingleThreadExecutor { runnable ->
            // The name shows in thread dumps and crash reports, so the thread is easy to identify.
            // A new thread copies the priority of the thread that creates it (usually the main
            // thread), so lower it: writing logs must not compete with UI work.
            val backgroundRunnable = Runnable {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                runnable.run()
            }
            Thread(backgroundRunnable, "steamclog-file-writer").apply { isDaemon = true }
        }
    }

    /**
     * Runs [block] on the file thread after every pending write, and waits for its result.
     * Never call it from the file thread itself: it would wait for itself forever.
     * Doc: docs/evergreen/log-files.md — never call from a task already on the file thread.
     */
    private fun <T> onFileThread(block: () -> T): T = fileExecutor.submit(Callable { block() }).get()

    override val needsCallerTag = true

    override fun accepts(priority: Int): Boolean {
        // Before #187 this tree was planted only once a config had a fileWritePath.
        return SteamcLog.isInitialized && SteamcLog.config.fileWritePath != null &&
                isLoggable(SteamcLog.config.logLevel.disk, priority)
    }

    //---------------------------------------------
    // Allows us to print out to an external file if desired.
    //---------------------------------------------
    // Doc: docs/evergreen/log-files.md — build the line on the calling thread, queue only the write.
    override fun write(event: LogEvent) {
        // Built on the calling thread: the tag, thread name and timestamp all describe the caller.
        val fullMessage = generateFullLogMessage(event, includeTimestamp = true, includeEmoji = false)

        // Queue the write and return at once; the file thread writes it after any earlier lines.
        // printLogToExternalFile catches write errors, so a failing disk never crashes the app.
        // The file thread only swaps in the already-transformed exception messages.
        fileExecutor.execute {
            printLogToExternalFile(fullMessage)
            event.originalThrowable?.let {
                printLogToExternalFile(event.transformStackTrace(Log.getStackTraceString(it)))
            }
        }
    }

    /**
     * Waits up to [timeoutMs] for every pending line to be written. Used before the process is
     * killed, so the last lines (such as a fatal log) are not lost. Never throws: on timeout the
     * queued lines keep being written in the background and the caller goes on.
     * Doc: docs/evergreen/log-files.md — flush before the process ends, with a timeout.
     */
    internal fun flush(timeoutMs: Long) {
        try {
            fileExecutor.submit { }.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            // get() clears the caller's interrupt flag when it throws; set it again so the
            // caller's own code still sees that it was interrupted.
            Thread.currentThread().interrupt()
            logToConsole("Log file flush was interrupted: $e")
        } catch (e: Exception) {
            logToConsole("Log file flush did not finish: $e")
        }
    }

    /**
     * Like [getLogFiles], for use inside a log call (attaching files to a user report): waits at
     * most [timeoutMs] and never throws. Returns null if the files could not be listed in time.
     * Doc: docs/evergreen/log-files.md — reads inside a log call have a timeout and never throw.
     */
    internal fun getLogFilesOrNull(sort: LogSort, timeoutMs: Long): List<File>? {
        return try {
            fileExecutor.submit(Callable { sortedLogFiles(sort) }).get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            logToConsole("Log files not listed, interrupted: $e")
            null
        } catch (e: Exception) {
            logToConsole("Log files not listed: $e")
            null
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

    /**
     * Deletes log files older than [Config.keepLogsForDays], on the file thread. Returns without
     * waiting; a later read still sees the result, because the file thread runs work in order.
     */
    internal fun removeOldLogFiles() {
        // execute(), not onFileThread(): nobody needs the result, so the caller (initWith, or a
        // user report) does not wait for the disk.
        fileExecutor.execute {
            removeOldLogFiles(getExternalLogDirectory(), SteamcLog.config.keepLogsForDays, System.currentTimeMillis())
        }
    }

    /**
     * Deletes the files in [directory] last modified more than [keepLogsForDays] days before [now],
     * on the calling thread. Tests call this directly with their own values.
     */
    internal fun removeOldLogFiles(
        directory: File?,
        keepLogsForDays: Int,
        now: Long
    ) {
        val deleteThese = mutableListOf<File>()
        // #147: Long arithmetic; as an Int, 25 days or more overflows and every file is deleted.
        val expiryMs = keepLogsForDays * 86_400_000L // (86400000 ms per day)

        for (file in directory?.listFiles() ?: emptyArray()) {
            if (file.lastModified() < (now - expiryMs)) deleteThese.add(file)
        }

        for (file in deleteThese) {
            if (!file.delete()) {
                logToConsole("Failed to delete log file ${file.name}")
            }
        }
    }

    /**
     * Returns the list of log files written to the log directory, sorted by the desired method.
     * Blocks until every line logged before the call is written.
     */
    fun getLogFiles(sort: LogSort): List<File>? = onFileThread { sortedLogFiles(sort) }

    private fun sortedLogFiles(sort: LogSort): List<File>? {
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
     * Blocks until every line logged before the call is written and every file is read.
     */
    internal fun getLogFileContents(): String = onFileThread {
        // Already on the file thread: call the direct versions, not the ones that queue work.
        removeOldLogFiles(getExternalLogDirectory(), SteamcLog.config.keepLogsForDays, System.currentTimeMillis())
        val logBuilder = StringBuilder()
        for (file in sortedLogFiles(LogSort.LastModifiedAsc) ?: emptyList()) {
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

        logBuilder.toString()
    }
}

//-----------------------------------------------------------------------------
// Extensions / Helpers
//-----------------------------------------------------------------------------
/**
 * Determines if the log (at given android.util.Log priority) should be logged given the
 * current tree logging level.
 */
internal fun isLoggable(treeLevel: LogLevel, logPriority: Int): Boolean {
    return (treeLevel != LogLevel.None) && (logPriority >= treeLevel.javaLevel)
}

/**
 * IMPORTANT: Must be called from SteamclogTree.log (or other code inside
 * the SteamcLog library) for the caller frame to be found correctly. The search skips only
 * SteamcLog and Timber frames, so any other frame between the logging call and this method would
 * be picked up as the caller.
 *
 * This method helps us get around that fact that because we are wrapping Timber calls, the
 * stacktrace information associated with the location of the report is relative to the Steamclog
 * codebase, and not the location where actual sclog method was invoked.
 *
 * Since Timber's createStackElementTag is made unusable since getTag is final (and gives
 * us the incorrect stacktrace location), this method attempts to generate the desired stacktrace
 * by creating a dummy Throwable and searching its stacktrace for the first frame that is not part
 * of the Steamclog or Timber call chain. This works the same for calls made through SteamcLog and
 * for direct Timber calls (#144).
 *
 * This is based on how Timber generates the stacktrace location for itself normally.
 *
 * Never throws: if no caller frame is found, [fallbackTag] (the tag Timber computed) is used.
 */
private fun createCustomStackElementTag(fallbackTag: String?): String {
    // ---- Taken directly from Timber ----
    // DO NOT switch this to Thread.getCurrentThread().getStackTrace(). The test will pass
    // because Robolectric runs them on the JVM but on Android the elements are different.
    val stackTrace = Throwable().stackTrace
    // ------------------------------------

    // StackTagTest covers this search with unit tests. There is deliberately no extra check on
    // Debug builds any more: a logging call must never throw (#144).
    //
    // NOTE: If tags start pointing at the wrong location, then it's possible a library has been
    // updated which may have added a new frame to the call stack at this point (for example, a
    // class outside the skipped packages), and as such loggingFramePrefixes may need to be updated.
    // Place a debug break here, check the stackTrace array and find which frame sits between the
    // Steamclog/Timber frames and the actual logging call. Its package will most likely be the new
    // prefix to add.
    val element = findCallerFrame(stackTrace) ?: return fallbackTag ?: "steamclog"
    return element.asLocation()
}

/** The "(File.kt:42):method" form used for a log line's tag. */
private fun StackTraceElement.asLocation() = "($fileName:$lineNumber):$methodName"

/**
 * Which frames we need to skip in the call stack to get to the method that called our
 * steamclog logging method (or called Timber directly): the class name prefixes of every frame
 * that sits between the logging call site and SteamclogTree.
 * The trailing dot keeps the sample app (com.steamclock.steamclogsample) from matching.
 *
 * NOTE: These prefixes may need to change when libraries are updated, as those updates may affect
 * the state of the stack trace and how the stack is handled. They also rely on the SteamcLog and
 * Timber class names surviving minification: if proguard/R8 renames them, the frames are no longer
 * skipped and the tag points at a library frame.
 */
private val loggingFramePrefixes = listOf("com.steamclock.steamclog.", "timber.log.")

/**
 * Returns the first frame in [stackTrace] whose class starts with none of [skippedPrefixes]
 * (by default, the first frame outside SteamcLog and Timber), or null if there is none.
 */
internal fun findCallerFrame(
    stackTrace: Array<StackTraceElement>,
    skippedPrefixes: List<String> = loggingFramePrefixes
): StackTraceElement? {
    return stackTrace.firstOrNull { frame ->
        skippedPrefixes.none { prefix -> frame.className.startsWith(prefix) }
    }
}

/**
 * Put in front of the raw text when [Config.messageTransform] throws (#187), so a reader can tell
 * the line was not cleaned.
 */
internal const val TRANSFORM_FAILED_PREFIX = "[unscrubbed: messageTransform failed] "

/** Set once the first transform failure is reported to Sentry. Tests reset it. */
internal val transformFailureReported = AtomicBoolean(false)

/**
 * Returns [text] after [Config.messageTransform] (#187), or [text] unchanged when none is set.
 * [LogEvent] calls it once per piece of text per log call.
 *
 * Never throws: when the transform throws (an Error included, since a regex can overflow the
 * stack), returns the raw text after [TRANSFORM_FAILED_PREFIX].
 */
internal fun transformLogText(text: String): String {
    // Every destination drops logs made before initWith, so only direct callers reach this
    // without a config.
    if (!SteamcLog.isInitialized) return text
    val transform = SteamcLog.config.messageTransform ?: return text
    return try {
        transform(text)
    } catch (t: Throwable) {
        reportTransformFailure(t, text.length)
        // #187: a deliberate choice to keep the line rather than drop it. It can write or send
        // the values the transform was meant to remove; the prefix marks those lines.
        TRANSFORM_FAILED_PREFIX + text
    }
}

/**
 * Reports a transform failure without the text it failed on: the exception class, the length of the
 * text, where the log call was made and, if found, the transform's own frame. Logged to Logcat each
 * time, and sent to Sentry once per process so a broken transform is noticed, when the config's
 * remote level takes warnings.
 */
private fun reportTransformFailure(failure: Throwable, textLength: Int) {
    // Not the exception's message: it may contain the text.
    val logCall = createCustomStackElementTag("unknown")
    // For a stack overflow the frame may be missing: the stack trace keeps only the deepest frames.
    val transformFrame = findCallerFrame(failure.stackTrace, nonAppFramePrefixes)
    val description = "SteamcLog messageTransform threw ${failure::class.java.name} " +
            "on $textLength characters; log call at $logCall; " +
            "transform frame ${transformFrame?.asLocation() ?: "unknown"}"
    logToConsole(description)
    // The Debug presets send nothing to Sentry, so neither does this report.
    if (isLoggable(SteamcLog.config.logLevel.remote, Log.WARN) &&
        transformFailureReported.compareAndSet(false, true)) {
        Sentry.captureMessage(description, SentryLevel.WARNING)
    }
}

/** Frames that cannot be the app's transform: platform, Kotlin, SteamcLog and Timber code. */
private val nonAppFramePrefixes = listOf(
    "java.", "javax.", "jdk.", "sun.", "kotlin.", "android.", "dalvik.", "libcore."
) + loggingFramePrefixes

/** Returns [throwable] and every cause and suppressed throwable its stack trace prints, once each. */
private fun printedThrowables(throwable: Throwable): Set<Throwable> {
    val printed = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    collectPrintedThrowables(throwable, printed)
    return printed
}

/** Adds [throwable], its causes and its suppressed throwables to [into], once each. */
private fun collectPrintedThrowables(throwable: Throwable, into: MutableSet<Throwable>) {
    if (!into.add(throwable)) return
    throwable.cause?.let { collectPrintedThrowables(it, into) }
    for (suppressed in throwable.suppressed) collectPrintedThrowables(suppressed, into)
}

/**
 * Returns based on given parameters:
 * - [emoji][Message][: Extra data]
 *
 * The message and extra data are [LogEvent.text], already transformed; the emoji is not.
 */
private fun generateSimpleLogMessage(event: LogEvent, includeEmoji: Boolean): String {
    val emoji = LogLevel.getLogLevel(event.priority)?.emoji
    val emojiStr = if (includeEmoji && emoji != null) { "$emoji " } else { "" }
    return "$emojiStr${event.text}"
}

/**
 * Returns based on given parameters:
 * - [emoji][Message][: Extra data]
 * - [Stack trace of the original throwable, if any]
 *
 * Timber.DebugTree ignores the throwable passed to its log method, so the stack trace must be part
 * of the message to reach Logcat (#148).
 */
internal fun generateConsoleLogMessage(event: LogEvent): String {
    val simpleMessage = generateSimpleLogMessage(event, includeEmoji = true)
    val originalThrowable = event.originalThrowable ?: return simpleMessage
    return "$simpleMessage\n${event.transformStackTrace(originalThrowable.stackTraceToString())}"
}

/**
 * Returns based on given parameters:
 * - [timestamp][package name][processId:threadId] [priority] [stackTag] > [emoji][Message][: Extra data]
 */
private fun generateFullLogMessage(event: LogEvent,
                                   includeTimestamp: Boolean,
                                   includeEmoji: Boolean): String {
    val logTimestampFormat = "yyyy-MM-dd'.'HH:mm:ss.SSS"
    val logTimeStamp = if (includeTimestamp) {
        "${SimpleDateFormat(logTimestampFormat, Locale.US).format(Date())} "
    } else {
        ""
    }

    val fullMessage = generateSimpleLogMessage(event, includeEmoji)
    return logTimeStamp +
            BuildConfig.LIBRARY_PACKAGE_NAME +
            "[${android.os.Process.myPid()}:${Thread.currentThread().name}] " +
            "[${event.priority}] [${event.callerTag}] > $fullMessage"
}

/**
 * Used mostly for Steamclog to report message using Log (not Timber), since using
 * Timber may lead to infinite calls in the library.
 */
@SuppressLint("LogNotTimber")
internal fun logToConsole(message: String) {
    Log.v("steamclog", message)
}