// This test lives outside the com.steamclock.steamclog package on purpose: findCallerFrame skips
// every frame in that package, so a test inside it would never be found as the caller.
package com.steamclock.stacktagtest

import com.steamclock.steamclog.Config
import com.steamclock.steamclog.LogDestination
import com.steamclock.steamclog.LogEvent
import com.steamclock.steamclog.LogLevelPreset
import com.steamclock.steamclog.SteamclogThrowableWrapper
import com.steamclock.steamclog.SteamclogTree
import com.steamclock.steamclog.clog
import com.steamclock.steamclog.findCallerFrame
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import timber.log.Timber

class StackTagTest {

    /** Records what SteamclogTree would compute, without touching android.util.Log. */
    private class RecordingTree : Timber.Tree() {
        var caller: StackTraceElement? = null
        var wrapper: SteamclogThrowableWrapper? = null

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            // Frame 0 is this tree. In production that frame is SteamclogTree inside the library,
            // so drop it to match what findCallerFrame sees there.
            val stackTrace = Throwable().stackTrace
            caller = findCallerFrame(stackTrace.copyOfRange(1, stackTrace.size))
            wrapper = SteamclogThrowableWrapper.from(t, message)
        }
    }

    private val tree = RecordingTree()

    @Before
    fun setUp() {
        Timber.plant(tree)
    }

    @After
    fun tearDown() {
        Timber.uproot(tree)
    }

    @Test
    fun directTimberCall_findsCallerFrame() {
        Timber.d("hello")

        val caller = requireNotNull(tree.caller) { "No caller frame found" }
        assertEquals(StackTagTest::class.java.name, caller.className)
        assertEquals("directTimberCall_findsCallerFrame", caller.methodName)
    }

    @Test
    fun findCallerFrame_skipsLibraryAndTimberFrames() {
        val stack = arrayOf(
            frame("com.steamclock.steamclog.DestinationsKt"),
            frame("com.steamclock.steamclog.ConsoleDestination"),
            frame("timber.log.Timber\$Tree"),
            frame("timber.log.Timber\$Forest"),
            frame("com.steamclock.steamclog.SteamcLog"),
            frame("com.steamclock.steamclogsample.MainActivity"),
        )

        assertEquals("com.steamclock.steamclogsample.MainActivity", findCallerFrame(stack)?.className)
    }

    @Test
    fun findCallerFrame_returnsNullWhenOnlyLibraryFrames() {
        val stack = arrayOf(
            frame("com.steamclock.steamclog.DestinationsKt"),
            frame("timber.log.Timber\$Forest"),
        )

        assertNull(findCallerFrame(stack))
    }

    @Test
    fun timberErrorWithMessage_keepsCallerMessage() {
        Timber.e(IllegalStateException("Throwable whose message the caller's message replaces"), "Failed to load")

        assertEquals("Failed to load", tree.wrapper?.originalMessage)
    }

    @Test
    fun timberErrorWithoutMessage_usesThrowableMessage() {
        val throwableMessage = "Throwable message used when Timber.e has no message"
        Timber.e(IllegalStateException(throwableMessage))

        assertEquals(throwableMessage, tree.wrapper?.originalMessage)
    }

    @Test
    fun steamclogWrapper_isPassedThroughUnchanged() {
        val wrapper = SteamclogThrowableWrapper("Failed to load", IllegalStateException("Throwable inside a wrapper passed straight to Timber"), false, null, null)

        Timber.e(wrapper)

        assertSame(wrapper, tree.wrapper)
    }

    /** A destination that keeps the caller tag SteamclogTree gives it (#187). */
    private class TagRecordingDestination : LogDestination {
        val tags = mutableListOf<String?>()
        override val needsCallerTag = true
        override fun accepts(priority: Int) = true
        override fun write(event: LogEvent) { tags += event.callerTag }
    }

    @Test
    fun steamclogTree_tagsDirectTimberAndClogCalls_withTheCaller() {
        // Release has no console level, so nothing calls android.util.Log.
        clog.initWith(Config(isDebug = false, fileWritePath = null, logLevel = LogLevelPreset.Release))
        val destination = TagRecordingDestination()
        val steamclogTree = SteamclogTree(listOf(destination))
        Timber.plant(steamclogTree)
        try {
            Timber.d("direct Timber call tagged by this test"); val timberLine = Throwable().stackTrace[0].lineNumber
            clog.info("clog call tagged by this test"); val clogLine = Throwable().stackTrace[0].lineNumber

            val method = "steamclogTree_tagsDirectTimberAndClogCalls_withTheCaller"
            assertEquals(
                listOf("(StackTagTest.kt:$timberLine):$method", "(StackTagTest.kt:$clogLine):$method"),
                destination.tags
            )
        } finally {
            Timber.uproot(steamclogTree)
        }
    }

    private fun frame(className: String) = StackTraceElement(className, "method", "File.kt", 1)
}
