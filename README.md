# SteamcLog Android
[Current Proposal Spec](https://docs.google.com/document/d/1GeFAMBn_ZrIP7qVLzcYlCfqDnPiCrgMa0JdrU8HRx94/edit?usp=sharing)
[iOS Version](https://github.com/steamclock/steamclog)

An open source library that consolidates/formalizes the logging setup and usage across all of Steamclock's projects.

## Installation

Current supported MinSDK: 21
The library is not yet hosted publically, so access to the aar file is required.

Steps to install Steamclog:
1. Create an `aars` folder in your project's app module (if it does not already exist)
2. Place the `steamclog-release.aar` file into the `aars` folder.
3. Add the following to your app module's build.gradle file:
    ```
    implementation files('aars/steamclog-debug.aar')
    ```
4. Sync your project grdle files
5. `Steamclog`singleton should now be available

## Initalization

Note: Steamclog can also be accessed via the `clog` typealias.

### Default support

Out of the box, Steamclog will have support for printing to the console, and Firebase Crashlytics (assuming your project has enabled Firebase Crashlytics and includes the required `google-services.json` file).

To setup Firebase Crashyltics in your project, see https://firebase.google.com/docs/crashlytics/get-started?platform=android

### Enabling File Logging

The `Steamclog.initWith` method can be used to enable external logging; this method only needs to be called once, and can be done in your Application object's `onCreate` method.

To enable Steamclog to write to the device, you must specify where the files should be stored, this is most easily done by passing along the application's `externalCacheDir` or `cacheDir` references.
```
clog.initWith(fileWritePath = externalCacheDir)
```
 * See https://developer.android.com/training/data-storage for details on the pros and cons of each.
 
### Enabling Firebase Analytics 
To setup Firebase Analytics in your project, see https://firebase.google.com/docs/analytics/get-started?platform=android

Due to current limitations on how the firebase plugin is applied to projects, your application must pass along an instance to the FirebaseAnalytics object before it can track analytics.

The `Steamclog.initWith` method can be used to enable firebase analytics; this method only needs to be called once, and can be done in your Application object's `onCreate` method. If your application has setup Firebase Analytics correctly, the `Firebase.analytics` object should be available to be passed to the initWith method.

```
clog.initWith(firebaseAnalytics = Firebase.analytics)
```

Both logging and analytics can be initalized at the same time:
```
clog.initWith(fileWritePath = externalCacheDir, firebaseAnalytics = Firebase.analytics)
```

## Configuration

SteamcLog has a number of configuration options

### logLevel: LogLevelPreset
Default value is: `develop`.
There are four log level presets available, each of which has different logging outputs.

| LogLevelPreset    | Disk Level | System Level | Remote Level |
|-------------------|------------|--------------|--------------|
| `firehose`        | verbose    | verbose      | none         |
| `develop`         | none       | debug        | none         |
| `release`         | none       | none         | warn         |
| `releaseAdvanced` | verbose    | none         | warn         |

In most cases, you'll be able to get by using `firehose` or `develop` on debug builds, and `release` or `releaseAdvanced` for production builds.
Note that if you're using `releaseAdvanced` you must build in a way for the client to email you the disk logs.

### requireRedacted: Bool
Default value is `false`.
Require that all logged objects conform to Redacted or are all redacted by default.

## Usage

From there, you can use `clog` anywhere in your application with the following levels. Note that availability of these logs will depend on your Configuration's `logLevel`.

`clog.verbose` - Log all of the things! Probably only output to the console by developers, never to devices.
`clog.debug` - Info that is interesting to developers, any information that may be helpful when debugging. Should be stored to system logs for debug builds but never stored in production.
`clog.info` - Routine app operations, used to document changes in state within the application. Minimum level of log stored in device logs in production.
`clog.warn` - Developer concerns or incorrect state etc. Something’s definitely gone wrong, but there’s a path to recover
`clog.error` - Something has gone wrong, report to a remote service (like Crashlytics)
`clog.fatal` - Something has gone wrong and we cannot recover, so force the app to close.

### Basic Signatures

Each of these functions has the following 2 available signatures:
`clog.<level>(_ message: String)`
`clog.<level>(_ message: String, object: Any)`

If `requireRedacted` is set to `true`, then the Any object *must* implement the Redactable interface, else all properties will be shown as `<REDACTED>`.

### Error and Fatal Specific Signatures
Error and Fatal levels have 2 more signatures that allows a given Throwable to be associated with the log. 
`clog.<level>(_ message: String, throwable: Throwable)`
`clog.<level>(_ message: String, throwable: Throwable,  object: Any)`

If no `Throwable` object is given for an error or fatal log, Steamclog will create a generic `NonFatalException` instance that will be used to generate crash reports on Crashlytics.

## Exporting Logs

If logging to a file has been enabled, then files, you can get the log file contents using `clog.getLogFileContents(): String?`

### Variable Redaction

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
