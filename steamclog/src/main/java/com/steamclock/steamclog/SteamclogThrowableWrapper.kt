package com.steamclock.steamclog

/**
 * SteamclogThrowableWrapper
 * Enables multiple types of data to be passed to our Destinations.
 *
 * Created by shayla on 2020-09-28
 */
data class SteamclogThrowableWrapper(
    val originalMessage: String,
    val originalThrowable: Throwable?,
    val attachLogFiles: Boolean?,
    val redactedObjectData: String?,
    val extraInfo: Map<String, Any>?): Throwable(originalMessage)
{
    companion object {
        fun from(throwable: Throwable?): SteamclogThrowableWrapper? {
            if (throwable == null) return null
            return throwable as? SteamclogThrowableWrapper
                ?: SteamclogThrowableWrapper(
                    throwable.message ?: throwable.toString(),
                    originalThrowable = throwable,
                    attachLogFiles = false, // Currently only attaching logs on User Reports.
                    redactedObjectData = null,
                    extraInfo = null
                )
        }

        /**
         * Like [from], but keeps the caller's message for a direct Timber call such as
         * `Timber.e(e, "Failed to load")` (#144). Timber passes the tree that message with the
         * throwable's stack trace appended; this strips the stack trace back off. When the caller
         * gave no message (`Timber.e(e)`), Timber passes only the stack trace, and the throwable's
         * own message is used, as [from] does.
         */
        internal fun from(throwable: Throwable?, timberMessage: String): SteamclogThrowableWrapper? {
            if (throwable == null || throwable is SteamclogThrowableWrapper) {
                return from(throwable)
            }
            val stackTraceSuffix = "\n" + throwable.stackTraceToString()
            if (!timberMessage.endsWith(stackTraceSuffix)) return from(throwable)
            return from(throwable)?.copy(originalMessage = timberMessage.removeSuffix(stackTraceSuffix))
        }
    }
}