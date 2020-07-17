package com.steamclock.steamclogsample

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.steamclock.steamclog.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        title = "SteamClog Test"

        // UI init
        demo_text.text = clog.toString()
        log_things.setOnClickListener { testAllLoggingLevels() }
        dump_file_button.setOnClickListener { testLogDump() }
        non_fatal.setOnClickListener { testNonFatal() }
        track_analytic.setOnClickListener { testTrackAnalytic() }

        level_selector.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                clog.config.logLevel = when (position) {
                    0 -> LogLevelPreset.Firehose
                    1 -> LogLevelPreset.Develop
                    2 -> LogLevelPreset.Release
                    3 -> LogLevelPreset.ReleaseAdvanced
                    else -> LogLevelPreset.Firehose
                }
            }
        }
    }

    private fun testAllLoggingLevels() {
        simulateCrash()

        clog.verbose("Verbose message")
        clog.verbose("Verbose message", RedactableParent())

        clog.debug("Debug message")
        clog.debug("Debug message", RedactableParent())

        clog.info("Info message")
        clog.info("Info message", RedactableParent())

        clog.warn("Warn message")
        clog.warn("Warn message", RedactableParent())

        Toast.makeText(applicationContext, "Logged some things! Check your console or show the dump the log.", Toast.LENGTH_LONG).show()
    }

    private fun testNonFatal() {
        clog.error("Error message")
        clog.error("Error message", RedactableParent())
        clog.error("Error message", Throwable("OriginalNonFatalThrowable"))
        clog.error("Error message", Throwable("OriginalNonFatalThrowable"), RedactableParent())
    }

    private fun testFatalCrash() {
        // These will crash app
            clog.fatal("Fatal message")
//            clog.fatal("Fatal message", TestMe())
//            clog.fatal(Throwable("OriginalFatalThrowable"),"Fatal message")
//            clog.fatal(Throwable("OriginalFatalThrowable"),"Fatal message", TestMe())
    }

    private fun testTrackAnalytic() {
        clog.track(AnalyticEvent.TestButtonPressed.id, AnalyticEvent.TestButtonPressed.data)
        clog.track(AnalyticEvent.TestButtonPressedWithRedactable.id, AnalyticEvent.TestButtonPressedWithRedactable.data)
    }

    private fun testLogDump() = GlobalScope.launch(Dispatchers.Main) {
        demo_text?.text = SteamcLog.getLogFileContents()
    }

    private fun simulateCrash() {
        throw RuntimeException("Test Crash") // Force a crash
    }

    // Test logging objects
    class RedactableParent : Any(), Redactable {
        val safeProp = "name"
        val secretProp = "WHOOPS"
        val safeRedactedChild = RedactableChild()
        val safeChild = NotRedactedChild()
        val secretChild = NotRedactedChild()

        override val safeProperties: Set<String> = HashSet<String>(setOf("safeProp", "safeRedactedChild", "safeChild"))
    }

    class RedactableChild : Any(), Redactable {
        val safeProp = 22
        val secretProp = "WHOOPS"

        override val safeProperties: Set<String> = HashSet<String>(setOf("safeProp"))
    }

    class NotRedactedChild : Any() {
        val prop1 = "doggo"
        val prop2 = 5
    }

    sealed class AnalyticEvent(val id: String, val data: Map<String, Any>) {
        object TestButtonPressed: AnalyticEvent("test_button_pressed", mapOf())
        object TestButtonPressedWithRedactable: AnalyticEvent("test_button_pressed", mapOf("redactableObject" to RedactableChild()))
    }
}