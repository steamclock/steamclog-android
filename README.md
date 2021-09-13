# SteamcLog Android

[Current Proposal Spec](https://docs.google.com/document/d/1GeFAMBn_ZrIP7qVLzcYlCfqDnPiCrgMa0JdrU8HRx94/edit?usp=sharing)

[iOS Version](https://github.com/steamclock/steamclog)

An open source library that consolidates/formalizes the logging setup and usage across all of Steamclock's projects.

## Table of Contents
- [Development](#development)
  * [Deploying new versions](#deploying-new-versions)
- [Usage](#usage)
  * [Installation](#installation)
  * [Initialization (Required)](#initialization--required-)
  * [Initialization (Optional)](#initialization--optional-)
  * [Enabling Sentry Reporting](#enabling-sentry-reporting)
    + [Enabling a Throwable/Exception "Block List"](#enabling-a-throwable-exception--block-list-)
  * [Common logging methods](#common-logging-methods)
    + [Basic Signatures](#basic-signatures)
    + [Error and Fatal Specific Signatures](#error-and-fatal-specific-signatures)
  * [Exporting Logs](#exporting-logs)
    + [Variable Redaction](#variable-redaction)
  * [Firebase](#firebase)
    + [Enabling Firebase Crashlytics (No longer supported)](#enabling-firebase-crashlytics--no-longer-supported-)
    + [Enabling Firebase Analytics (No longer supported)](#enabling-firebase-analytics--no-longer-supported-)

<small><i><a href='http://ecotrust-canada.github.io/markdown-toc/'>Table of contents generated with markdown-toc</a></i></small>

## Development

### Deploying new versions

These steps are for developers looking to create a new release of the Steamclog library; if this does not pertain to you, please skip down to the **Installation** section.

Currently we are hosting the library on [Jitpack](https://jitpack.io/), to deploy a new version of the library:

1. Push all changes to master
2. From within the GitHub repo, navigate to the Code panel; on the right side should be a **Releases** section
3. Click on **Releases** (which should take you [here](https://github.com/steamclock/steamclog-android/releases))
4. Make note of the latest release version name (ie. v1.1)
5. Click the **Draft a new release** button on the right
6. Set the **Tag Version**; it's best use the last release version as a guide (ie. set as v1.2)
7. Set **Release Title** to be "Steamclog <Version>"
8. Description is optional, could set changelog here if desired
9. Click the **Publish Release** button at the bottom
10. Verify on the [Jitpack page for the Steamclog project](https://jitpack.io/#steamclock/steamclog-android) that the new version is available
11. Update projects using Steamclog with the new version

## Usage

### Installation

Steamclog is currently hosted on Jitpack; to include it in your project:

1. Add this in your **root** build.gradle at the end of repositories:
``` 
	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
``` 

2. Add the dependency in your app module's build.gradle:
```
	dependencies {
	    implementation 'com.github.steamclock:steamclog-android:<VERSION>'
	}
```
Most recent version can be found [here](https://github.com/steamclock/steamclog-android/releases)

3. Sync your project gradle files

4. `Steamclog` singleton should now be available

### Initialization (Required)

To initialize Steamclog call `clog.initWith` in your app's custom Application class's `onCreate` method.
This function takes the following required properties:
* `isDebug` (Boolean): If application is considered to be a debug build; in most cases this is your application's `BuildConfig.DEBUG` flag. Determines the default Log Level Presets Steamclog selects for the build.
* `fileWritePath` (File): Location where the file destination will write to. This is most easily done by passing along the application's `externalCacheDir` or `cacheDir` file paths.

Example:
```
class App: Application() {
...
   override fun onCreate() {
      clog.initWith(BuildConfig.DEBUG, externalCacheDir)
   }      
```

Once initialized, this will give out of the box support for printing to the console and writing to a file. Further steps are required to enable remote logging to Sentry.

### Initialization (Optional)

The Config object has the following optional properties which may be overridden if desired. These may be set initially when calling `initWith` or updated at run time.

`Config`:
* `keepLogsForDays` (Int, default 3): Determines how long generated log files are kept for.
* `autoRotateConfig` (AutoRotateConfig, see below): Configuration for auto-rotating file behaviour
* `requireRedacted` (Boolean, default false): Indicates if any extra objects being logged must implement the redacted interface. For apps with very sensitive data models, it is suggested to set this to true.
* `logLevel`: (LogLevelPreset, default based on `isDebug` setting): Destination logging levels; it is recommended to use the defaults set by Steamclog instead of initializing these manually. In special cases where more data is desired, update this property.

`AutoRotateConfig`:
* `fileRotationSeconds` (Long, default 600): The number of seconds before a log file is rotated; default of 600 == 10 mins.

Example:
```
class App: Application() {
...
   override fun onCreate() {
        clog.initWith(Config(
            BuildConfig.DEBUG,
            externalCacheDir,
            keepLogsForDays = 3,
            autoRotateConfig = AutoRotateConfig(fileRotationSeconds = 600L),
            requireRedacted = false
        ))
   }      
```

### Enabling Sentry Reporting

To setup Sentry reporting in your project, see https://docs.sentry.io/platforms/android/

You will need to create a new application on the Sentry dashboard which requires admin access. If your project required Proguard or R8 please see the above Sentry documentation as some settings may need to be put in place to properly upload the mappings accordingly.

Once your main project has Sentry enabled no further work should be required for Steamclog to report to it.

#### Enabling a Throwable/Exception "Block List"
If you'd like to suppress a Throwable or Exception from _ever_ being logged as an error in your crash reporting system, the `throwableBlocker` interface can be overridden to allow the application to determine which Throwables should be be blocked. This can allow us to catch and redirect certain Throwables to be logged as an "App Health Analytic" instead. By default all Throwables will be logged as errors.

This can be setup at any point by implementing the `throwableBlocker` interface:
```
clog.throwableBlocker = ThrowableBlocker { throwable ->
  when (throwable) {
    is BlockedException1 -> {
        // For example, we could log this as an analytic.
        true
    }
    is BlockedException2 -> {
        // For example, we could want to do nothing and ignore this exception.
        // Or maybe we want to log this as info instead.
        true
    }
    else -> {
        // The Throwable is not blocked, and will be sent as an error to the crash reporting destination.
        false
    }
}
```

### Common logging methods

See https://coda.io/d/SteamcLog_dmRQYLbOZrl/API-Docs_sufrm#_luAYL for full details

`clog.verbose` - Log all of the things! Probably only output to the console by developers, never to devices.
`clog.debug` - Info that is interesting to developers, any information that may be helpful when debugging. Should be stored to system logs for debug builds but never stored in production.
`clog.info` - Routine app operations, used to document changes in state within the application. Minimum level of log stored in device logs in production.
`clog.warn` - Developer concerns or incorrect state etc. Something’s definitely gone wrong, but there’s a path to recover
`clog.error` - Something has gone wrong, report to a remote service (like Sentry)
`clog.fatal` - Something has gone wrong and we cannot recover, so force the app to close.

#### Basic Signatures

Each of these functions has the following 2 available signatures:
`clog.<level>(_ message: String)`
`clog.<level>(_ message: String, object: Any)`

If `requireRedacted` is set to `true`, then the Any object *must* implement the Redactable interface, else all properties will be shown as `<REDACTED>`.

#### Error and Fatal Specific Signatures
Error and Fatal levels have a special signature that allows a given Throwable to be associated with the log.
`clog.<level>(_ message: String, throwable: Throwable,  object: Any)`

If no `Throwable` object is given for an error or fatal log, Steamclog will create a generic `NonFatalException` instance that will be used to generate crash reports on Sentry.
Please note, an error will only be logged if the Throwable is _not_ in the blocked via the `ThrowableBlocker` interface implementation.

### Exporting Logs

If logging to a file has been enabled, then files, you can get the log file contents using `clog.getLogFileContents(): String?`

#### Variable Redaction

`Redacted` is a protocol that can be conformed to by a struct or class for marking particular fields as safe for logging. By default, a class implementing the `Redactable` will have all fields marked as redacted, and you can define logging-safe fields using the `safeProperties` field.

Example:
```kotlin
import com.steamclock.steamclog.*

data class User(val name: String, val email: String, val bankingSecret: String) : Redactable {
    override val safeProperties: Set<String> = HashSet<String>(setOf("name"))
}
```

In this case, when a `User` object is logged by Steamclog, it will log something like the following:
```
clog.info("Testing Redactable Class", User("shayla", "me@email.com", "123456"))
```
And the log will output:
`Testing Redactable Class : User(bankingSecret=<redacted>, email=<redacted>, name=shayla)`

### Firebase

#### Enabling Firebase Crashlytics (No longer supported)

_Firebase Crashlytics is no longer a supported destination for crash reporting_

#### Enabling Firebase Analytics (No longer supported)

_Firebase Analytics is no longer a supported for tracking analytics; tracking must be handled manually by the calling project_


