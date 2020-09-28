# SteamcLog Android

[Current Proposal Spec](https://docs.google.com/document/d/1GeFAMBn_ZrIP7qVLzcYlCfqDnPiCrgMa0JdrU8HRx94/edit?usp=sharing)

[iOS Version](https://github.com/steamclock/steamclog)

An open source library that consolidates/formalizes the logging setup and usage across all of Steamclock's projects.

## Table of Contents
  * [Development](#development)
    + [Deploying new versions](#deploying-new-versions)
  * [Usage](#usage)
    + [Installation](#installation)
    + [Initialization](#initialization)
      - [Enabling External File Logging](#enabling-external-file-logging)
    + [Enabling Sentry Reporting](#enabling-sentry-reporting)
      - [Enabling Firebase Crashlytics](#enabling-firebase-crashlytics)
      - [Enabling Firebase Analytics](#enabling-firebase-analytics)
    + [Configuration](#configuration)
      - [logLevel: LogLevelPreset](#loglevel--loglevelpreset)
      - [requireRedacted: Bool](#requireredacted--bool)
    + [Common methods](#common-methods)
      - [Basic Signatures](#basic-signatures)
      - [Error and Fatal Specific Signatures](#error-and-fatal-specific-signatures)
    + [Exporting Logs](#exporting-logs)
      - [Variable Redaction](#variable-redaction)

_[Table of contents generated with markdown-toc](http://ecotrust-canada.github.io/markdown-toc/)_

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

### Initialization

Steamclog can also be accessed via the `clog` typealias.

Out of the box, Steamclog will have support for
 * Printing to the console (no setup required)
 * External file logging (setup required)
 * Sentry reporting  (setup required)
 * Firebase Crashlytics (setup required)
 * Firebase Analytics (setup required)

#### Enabling External File Logging

The `Steamclog.initWith` method can be used to enable external logging; this method only needs to be called once, and can be done in your Application object's `onCreate` method.

To enable Steamclog to write to the device, you must specify where the files should be stored, this is most easily done by passing along the application's `externalCacheDir` or `cacheDir` references.
```
clog.initWith(fileWritePath = externalCacheDir)
```
 * See https://developer.android.com/training/data-storage for details on the pros and cons of each.
 
 
### Enabling Sentry Reporting 

To setup Sentry reporting in your project, see https://docs.sentry.io/platforms/android/

You will need to create a new application on the Sentry dashboard which requires admin access. If your project required Proguard or R8 please see the above Sentry documentation as some settings may need to be put in place to properly upload the mappings accordingly.

Once your main project has Sentry enabled no further work should be required for Steamclog to report to it.
 
#### Enabling Firebase Crashlytics 

To setup Firebase Crashyltics in your project, see https://firebase.google.com/docs/crashlytics/get-started?platform=android

Once your main project has Crashlytics enabled (ie. includes the required `google-services.json` file etc...) no further work should be required for Steamclog to report to it.
 

#### Enabling Firebase Analytics

To setup Firebase Analytics in your project, see https://firebase.google.com/docs/analytics/get-started?platform=android

Due to current limitations on how the firebase plugin is applied to projects, your application must pass along an instance to the FirebaseAnalytics object before it can track analytics.

The `Steamclog.initWith` method can be used to enable firebase analytics; this method only needs to be called once, and can be done in your Application object's `onCreate` method. If your application has setup Firebase Analytics correctly, the `Firebase.analytics` object should be available to be passed to the initWith method.

```
clog.initWith(firebaseAnalytics = Firebase.analytics)
```

Both logging and analytics can be initialized at the same time:
```
clog.initWith(fileWritePath = externalCacheDir, firebaseAnalytics = Firebase.analytics)
```

### Configuration

SteamcLog has a number of configuration options

#### logLevel: LogLevelPreset
Default value is: `develop`.
There are four log level presets available, each of which has different logging outputs.

| LogLevelPreset    | Disk Level | System Level | Remote Level |
|-------------------|------------|--------------|--------------|
| `firehose`        | verbose    | verbose      | none         |
| `develop`         | none       | debug        | none         |
| `release`         | none       | none         | warn         |
| `releaseAdvanced` | verbose    | none         | info         |

In most cases, you'll be able to get by using `firehose` or `develop` on debug builds, and `release` or `releaseAdvanced` for production builds.
Note that if you're using `releaseAdvanced` you must build in a way for the client to email you the disk logs.

#### requireRedacted: Bool
Default value is `false`.
Require that all logged objects conform to Redacted or are all redacted by default.

### Common methods

From there, you can use `clog` anywhere in your application with the following levels. Note that availability of these logs will depend on your Configuration's `logLevel`.

`clog.verbose` - Log all of the things! Probably only output to the console by developers, never to devices.
`clog.debug` - Info that is interesting to developers, any information that may be helpful when debugging. Should be stored to system logs for debug builds but never stored in production.
`clog.info` - Routine app operations, used to document changes in state within the application. Minimum level of log stored in device logs in production.
`clog.warn` - Developer concerns or incorrect state etc. Something’s definitely gone wrong, but there’s a path to recover
`clog.error` - Something has gone wrong, report to a remote service (like Crashlytics)
`clog.fatal` - Something has gone wrong and we cannot recover, so force the app to close.

#### Basic Signatures

Each of these functions has the following 2 available signatures:
`clog.<level>(_ message: String)`
`clog.<level>(_ message: String, object: Any)`

If `requireRedacted` is set to `true`, then the Any object *must* implement the Redactable interface, else all properties will be shown as `<REDACTED>`.

#### Error and Fatal Specific Signatures
Error and Fatal levels have 2 more signatures that allows a given Throwable to be associated with the log. 
`clog.<level>(_ message: String, throwable: Throwable)`
`clog.<level>(_ message: String, throwable: Throwable,  object: Any)`

If no `Throwable` object is given for an error or fatal log, Steamclog will create a generic `NonFatalException` instance that will be used to generate crash reports on Crashlytics.

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


