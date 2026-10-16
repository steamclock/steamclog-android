## Required if you want to use Steamclog Redactable classes, and Proguard/R8 to obsfucate code.
-keep class * extends com.steamclock.steamclog.Redactable { *; }
# #145: kotlin-reflect fails on a whole Redactable if a property's type has no Kotlin metadata,
# so keep the types of Redactable properties (and their names, so they print readably).
# Field types cover stored properties; getter return types cover computed and delegated ones.
-if class * extends com.steamclock.steamclog.Redactable { *** *; }
-keep class <2>
-if class * extends com.steamclock.steamclog.Redactable { *** get*(); }
-keep class <2>

# #145: createCustomStackElementTag skips Timber frames by class name (loggingFramePrefixes)
-keepnames class timber.log.**

# Required for run time verfication that CustomStackElementTag is correct
-keep,allowoptimization class com.steamclock.steamclog.* { *; }
