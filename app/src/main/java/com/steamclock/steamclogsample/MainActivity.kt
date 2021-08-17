package com.steamclock.steamclogsample

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.steamclock.steamclog.LogLevelPreset
import com.steamclock.steamclog.Redactable
import com.steamclock.steamclog.SteamcLog
import com.steamclock.steamclog.clog
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
        log_things.setOnClickListener { testLogging() }
        dump_file_button.setOnClickListener { testLogDump() }
        non_fatal.setOnClickListener { testNonFatal() }
        track_analytic.setOnClickListener {
            Toast.makeText(applicationContext, "Not supported", Toast.LENGTH_LONG).show()
        }

        add_user_id.setOnClickListener { clog.setUserId("1234") }

        demo_text.setOnLongClickListener {
            copyFileToClipboard()
            true
        }

        level_selector.setSelection(when (clog.config.logLevel) {
            LogLevelPreset.Firehose -> 0
            LogLevelPreset.Develop -> 1
            LogLevelPreset.Release -> 2
            LogLevelPreset.ReleaseAdvanced -> 3
            else -> 0
        })

        level_selector.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                clog.config.logLevel = when (position) {
                    0 -> {
                        LogLevelPreset.Firehose
                    }
                    1 -> {
                        LogLevelPreset.Develop
                    }
                    2 -> {
                        LogLevelPreset.Release
                    }
                    3 -> {
                        LogLevelPreset.ReleaseAdvanced
                    }
                    else -> {
                        LogLevelPreset.Firehose
                    }
                }
                demo_text.text = clog.toString()
                clog.warn("LogLevel changed to ${clog.config.logLevel.title}")
            }
        }
    }

    private fun testLogging() {
        testVerbose()
        testDebug()
        testWarn()
        testInfo()
        Toast.makeText(applicationContext, "Logged some things! Check your console or show the dump the log.", Toast.LENGTH_LONG).show()
    }

    private fun testVerbose() {
        clog.verbose("Verbose message")
        clog.verbose("Verbose message with data", RedactableParent())
    }

    private fun testDebug() {
        clog.debug("Debug message")
        clog.debug("Debug message with data", RedactableParent())
    }

    private fun testInfo() {
        clog.info("Info message")
        clog.info("Info message with data", RedactableParent())
    }

    private fun testWarn() {
        clog.warn("Warn message")
        clog.warn("Warn message with data", RedactableParent())
    }

    private fun testNonFatal() {
        // Won't create new tickets
        clog.info("Running testNonFatal")

        // Should create tickets
        //
        // NOTE, Sentry seems to apply some grouping logic such that the *same* Throwable thrown
        // in the *same* function will be grouped no matter what messages they are given, such
        // that the messages will overwrite each other.
        //
        // Since our examples are using the basic Throwable for testing, we need to call them in
        // separate methods for testing, but in practice we shouldn't see this case since more
        // unique Throwables will be used.
        testNonFatalMessageOnly()
        testNonFatalWithData()
        testNonFatalWithThrowable()
        testNonFatalWithThrowableAndData()
    }

    private fun testNonFatalMessageOnly() {
        clog.error("Error with message only")
    }

    private fun testNonFatalWithData() {
        clog.error("Error with Data", null, RedactableParent())
    }

    private fun testNonFatalWithThrowable() {
        clog.error("Error with Throwable", Throwable("Error with Throwable"), null)
    }

    private fun testNonFatalWithThrowableAndData() {
        clog.error("Error Throwable and Data", Throwable("Error with Throwable and Data"), RedactableParent())
    }

    private fun testFatalCrash() {
        clog.info("Running testFatalCrash")

        // These will crash app
            //clog.fatal("Fatal message")
//            clog.fatal("Fatal message", RedactableParent())
//            clog.fatal(Throwable("OriginalFatalThrowable"),"Fatal message")
            clog.fatal("Fatal message", Throwable("OriginalFatalThrowable"), RedactableParent())
    }

    private fun testLogDump() = GlobalScope.launch(Dispatchers.Main) {
        demo_text?.text = SteamcLog.getLogFileContents()
    }

    private fun simulateCrash() {
        clog.info("Simulating a run time crash")
        throw RuntimeException("Test Crash") // Force a crash
    }

    private fun copyFileToClipboard() {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("File Dump", demo_text?.text)
        clipboardManager.setPrimaryClip(clipData)
        Toast.makeText(applicationContext, "Copied to clipboard", Toast.LENGTH_LONG).show()
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