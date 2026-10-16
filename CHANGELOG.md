# Changelog
All notable changes to this project will be documented in this file.

The format is based on the Steamclock [Release Management Guide](https://github.com/steamclock/labs/wiki/Release-Management-Guide).

## Jitpack v2.5 : Unreleased
- Bumped compileSdk and targetSdk to 35, minSdk raised to 23
- Updated AGP 8.5.2 → 8.13.2, Gradle wrapper 8.7 → 9.0
- Enabled Gradle configuration cache for faster incremental builds
- Updated Kotlin 2.0.0 → 2.4.0
- Updated Sentry Android SDK 7.9.0 → 8.43.1, Sentry Gradle Plugin 4.5.1 → 6.10.0
- Updated kotlinx-coroutines 1.9.0 → 1.11.0, appcompat 1.7.0 → 1.7.1, constraintlayout 2.2.0 → 2.2.1, material 1.12.0 → 1.14.0
- Fixed Sentry 8.x API compatibility: clearUserId() and SentryEvent extras
- Modernized Gradle build structure: added version catalog, plugins block, centralized repository declarations, migrated to Kotlin DSL
- Fixed API 23 compatibility: replaced Java forEach calls with for loops in file management methods
- Fixed direct Timber calls crashing debug builds; the log tag now names the real caller, and `Timber.e(e, "msg")` keeps its message (#144)
- Fixed logged object descriptions (#146): cyclic objects no longer crash (a repeated object prints as `<visited ClassName>`), boxed primitives, enums and strings print their value instead of `Integer()`/`Kind()`, both as properties and when logged directly (`<redacted>` when `requireRedacted` is set), and a failure while describing an object prints `<description failed: ...>` instead of throwing. Log output changes for affected objects.
- Pinned R8 to 9.5.26 in the build, so minified builds can read Kotlin 2.4 metadata; AGP 8.13.2's bundled R8 could not (#137). Build-only change, no effect on the published library.
- Narrowed the consumer R8 rules (#145): removed `-keep public class *`, which stopped R8 from renaming most public classes in every consuming app and its dependencies. **Consumer build output changes:** release builds obfuscate and shrink more (the sample app's APK drops from 8.0 MB to 6.4 MB). The library now keeps only Timber's class names and the types of `Redactable` properties, which redaction and the log tag need.
- **Breaking:** `clog.fatal(...)` now matches the README (#142). It captures a Sentry event at `FATAL` level (with the same `FilterOut` check and extra info as errors; before, it only added a breadcrumb), flushes Sentry, then kills the app's process. It returns `Nothing` instead of `Unit`, so code after a `fatal` call is unreachable. Apps that used `fatal` as a severe log and kept running must switch those calls to `clog.error`.

---

## Jitpack v2.4 : Aug 15, 2023
- Allow more than one log to be attached to a Sentry report (#115)
- Fixed issue with log auto rotation (#115)
- Removed unused "track" code (#64)
- Config.extraInfo now optional (#114)

---

## Jitpack v2.3 : Jun 19, 2023
- Added UserReports (#110)
- Updated Sentry plugin version; added exclusion for "default" Timber/Sentry integration (#111)

---

## Jitpack v2.2 : Apr 17, 2023
- Updated to SDK 33, kotlin and Sentry dependencies (#108)

---

## Jitpack v2.1 : Aug 30, 2022

- Updated libraries including Sentry (#101)
- Replaced kotlin synthetics with ViewBinding in sample app (#47) 
- Update kotlin version, and dependencies again (part of #106)

---

## Jitpack v2.0 : Sep 21, 2021

- Added ThrowableBlocker functional interface to allow specific Throwables/Exceptions to be suppressed from being sent as an error to the crash reporting destination (#22)
- Update Sentry from 4.3.0 to 5.1.0
- [1] Add log rotation functionality
- Update Log Level Presets to match specs from TechWG discussion (#83)
- Updated to Gradle 7.0; updated 3rd party libraries (#86)
- Updated readme (#88)
- InitWith method takes Config parameter (#91)
- Renamed ThrowableBlocker to FilterOut (#95)
- Simplify Sentry setup (#84)

## Jitpack v1.9 : May 20th 2021

- Fixed Proguard/R8 crash occurring on Debug builds (#67)
- Fixed SetUserId crash

---

## Jitpack v1.7 : May 19th 2021

- Removed Firebase Crashlytics support (#62)
- Removed Firebase Analytics support (#62)
- Added support for Sentry to "setUserId" (#63)

---

## Jitpack v1.6 : May 13th 2021

- Update LogLevelPresets to default to release->info, releaseAdvanced->verbose (#45)
- Update 3rd party libraries (#48)
- Clean up Sentry report formatting (#51)

---

## Jitpack v1.3 : Feb 9th 2021

- Fixed default LogLevelPresets, app must now include debug info (#33)
- Fixed crash on Pixel 4 devices running Android 11 (updated Sentry)

---

## Jitpack v1.2 : Oct 6th 2020

- Updated default LogLevelPresets (#33)
- Changed releaseAdvanced LogLevelPresets (#11)

---

## Jitpack v1.1 : Sept 8th 2020

- Added changelog! (#12)
- Added PR template (#21)
- Added support for Sentry (#24)
- Added readme (#16)
- Attempting to use Jitpack (#2)
- Created initWith method that omits FirebaseAnalytics instance (#31)

---
