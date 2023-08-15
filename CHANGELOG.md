# Changelog
All notable changes to this project will be documented in this file.

The format is based on the Steamclock [Release Management Guide](https://github.com/steamclock/labs/wiki/Release-Management-Guide).

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
