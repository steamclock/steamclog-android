## Required if you want to use Steamclog Redactable classes, and Proguard/R8 to obsfucate code.
-keep class * extends com.steamclock.steamclog.Redactable { *; }

# Required so the log tag names the caller in minified builds: createCustomStackElementTag skips
# stack frames whose class starts with com.steamclock.steamclog., so these classes must keep that name.
-keep,allowoptimization class com.steamclock.steamclog.* { *; }