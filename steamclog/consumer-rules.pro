## Required if you want to use Steamclog Redactable classes, and Proguard/R8 to obsfucate code.
-keep class * extends com.steamclock.steamclog.Redactable { *; }

# Required for run time verfication that CustomStackElementTag is correct
-keep,allowoptimization class com.steamclock.steamclog.* { *; }