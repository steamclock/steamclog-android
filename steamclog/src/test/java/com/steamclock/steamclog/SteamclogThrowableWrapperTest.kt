package com.steamclock.steamclog

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import timber.log.Timber

/**
 * Checks that SteamcLog's internal wrapper captures no stack trace (#151). Timber turns the
 * throwable it is given into text for every tree; without frames that text stays short. A tree
 * added with addCustomTree receives that text (#149).
 */
class SteamclogThrowableWrapperTest {

    private val wrapper = SteamclogThrowableWrapper("message logged by this test", null, false, null, null)

    @Test
    fun wrapper_hasNoStackFrames() {
        assertTrue(wrapper.stackTrace.isEmpty())
    }

    @Test
    fun treeReceivingTheWrapper_getsTextWithoutStackFrames() {
        var received: String? = null
        val tree = object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                received = message
            }
        }

        // SteamcLog logs the wrapper as the throwable with no message, as this call does.
        tree.i(wrapper)

        val text = received!!
        assertTrue(text, text.contains("message logged by this test"))
        assertFalse(text, text.contains("\tat "))
    }
}
