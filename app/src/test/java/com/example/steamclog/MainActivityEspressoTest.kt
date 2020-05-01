package com.example.steamclog

import android.R
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class MainActivityEspressoTest {
    @Rule
    var mActivityRule: ActivityTestRule<MainActivity> = ActivityTestRule(MainActivity::class.java)

    @Test
    fun ensureTextChangesWork() { // Type text and then press the button.
        Espresso.onView(withId(R.id.inputField))
            .perform(ViewActions.typeText("HELLO"), closeSoftKeyboard())
        Espresso.onView(withId(R.id.changeText)).perform(click())
        // Check that the text was changed.
        Espresso.onView(withId(R.id.inputField)).check(matches(withText("Lalala")))
    }

    @Test
    fun changeText_newActivity() { // Type text and then press the button.
        Espresso.onView(withId(R.id.inputField)).perform(
            ViewActions.typeText("NewText"),
            closeSoftKeyboard()
        )
        Espresso.onView(withId(R.id.switchActivity)).perform(click())
        // This view is in a different Activity, no need to tell Espresso.
        Espresso.onView(withId(R.id.resultView)).check(matches(withText("NewText")))
    }
}