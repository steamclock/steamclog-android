package com.steamclock.steamclogsample

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.steamclock.steamclog.*
import com.steamclock.steamclogsample.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        val toolbar = binding.toolbar
        setSupportActionBar(toolbar)
        title = "SteamClog Test"
        setContentView(view)

        // UI init
        binding.demoText.text = clog.toString()
        binding.logThings.setOnClickListener { testLogging() }
        binding.dumpFileButton.setOnClickListener { testLogDump() }
        binding.allNonFatals.setOnClickListener { testAllNonFatals() }
        binding.singleNonFatal.setOnClickListener{ testSingleNonFatal() }
        binding.userReport.setOnClickListener { testUserReport() }
        binding.logBlockedException.setOnClickListener { testBlockedException() }
        binding.trackAnalytic.setOnClickListener {
            Toast.makeText(applicationContext, "Not supported", Toast.LENGTH_LONG).show()
        }

        binding.addUserId.setOnClickListener { clog.setUserId("1234") }

        binding.demoText.setOnLongClickListener {
            copyFileToClipboard()
            true
        }

        binding.levelSelector.setSelection(when (clog.config.logLevel) {
            LogLevelPreset.DebugVerbose -> 0
            LogLevelPreset.Debug -> 1
            LogLevelPreset.Release -> 2
            LogLevelPreset.ReleaseAdvanced -> 3
            else -> 0
        })

        binding.levelSelector.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View,
                position: Int,
                id: Long
            ) {
                clog.config.logLevel = when (position) {
                    0 -> {
                        LogLevelPreset.DebugVerbose
                    }
                    1 -> {
                        LogLevelPreset.Debug
                    }
                    2 -> {
                        LogLevelPreset.Release
                    }
                    3 -> {
                        LogLevelPreset.ReleaseAdvanced
                    }
                    else -> {
                        LogLevelPreset.DebugVerbose
                    }
                }
                binding.demoText.text = clog.toString()
                clog.warn("LogLevel changed to ${clog.config.logLevel.title}")
            }
        }
    }
    private fun showMessageIfCrashReportingNotEnabled() {
        if (clog.config.logLevel.remote == LogLevel.None) {
            Toast.makeText(applicationContext,
                "Set Log Level to Release or Release Advanced to enable crash reporting",
                Toast.LENGTH_LONG).show()

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

    private fun testBlockedException() {
        showMessageIfCrashReportingNotEnabled()

        clog.error("Testing BlockedException1",
            BlockedException1("This should not trigger a crash report"),
            null)

        clog.error("Testing BlockedException2",
            BlockedException2("This should also not trigger a crash report"),
            null)

        clog.error("Testing AllowedException",
            AllowedException("This SHOULD trigger a crash report"),
            null)

        Toast.makeText(applicationContext,
            "If enabled, crash reporter should have logged AllowedException only",
            Toast.LENGTH_LONG).show()
    }

    private fun testUserReport() {
        showMessageIfCrashReportingNotEnabled()
        clog.info("Running testUserReport")
        clog.userReport("This is my user report")
    }

    private fun testSingleNonFatal() {
        showMessageIfCrashReportingNotEnabled()
        clog.info("Running testSingleNonFatal")
        testNonFatalMessageOnly()
    }

    private fun testAllNonFatals() {
        showMessageIfCrashReportingNotEnabled()
        clog.info("Running testAllNonFatals")

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
        showMessageIfCrashReportingNotEnabled()
        clog.info("Running testFatalCrash")

        // These will crash app
            //clog.fatal("Fatal message")
//            clog.fatal("Fatal message", RedactableParent())
//            clog.fatal(Throwable("OriginalFatalThrowable"),"Fatal message")
            clog.fatal("Fatal message", Throwable("OriginalFatalThrowable"), RedactableParent())
    }

    private fun testLogDump() = GlobalScope.launch(Dispatchers.Main) {
        binding.demoText.text = SteamcLog.getFullLogContents()
    }

    private fun simulateCrash() {
        clog.info("Simulating a run time crash")
        throw RuntimeException("Test Crash") // Force a crash
    }

    private fun copyFileToClipboard() {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("File Dump", binding.demoText.text)
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