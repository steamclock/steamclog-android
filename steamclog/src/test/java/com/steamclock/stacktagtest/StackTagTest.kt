// This test lives outside the com.steamclock.steamclog package on purpose: findCallerFrame skips
// every frame in that package, so a test inside it would never be found as the caller.
package com.steamclock.stacktagtest

import com.steamclock.steamclog.SteamclogThrowableWrapper
import com.steamclock.steamclog.findCallerFrame
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import timber.log.Timber

class StackTagTest {

    /** Records what a Destination would compute, without touching android.util.Log. */
    private class RecordingTree : Timber.Tree() {
        var caller: StackTraceElement? = null
        var wrapper: SteamclogThrowableWrapper? = null

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            // Frame 0 is this tree. In production that frame is a Destination inside the library,
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
        Timber.e(IllegalStateException("boom"), "Failed to load")

        assertEquals("Failed to load", tree.wrapper?.originalMessage)
    }

    @Test
    fun timberErrorWithoutMessage_usesThrowableMessage() {
        Timber.e(IllegalStateException("boom"))

        assertEquals("boom", tree.wrapper?.originalMessage)
    }

    private fun frame(className: String) = StackTraceElement(className, "method", "File.kt", 1)
}
