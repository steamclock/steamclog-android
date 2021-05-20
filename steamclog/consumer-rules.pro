# Required if you want to use Steamclog Redactable classes, and Proguard/R8 to obsfucate code.
-keep class * extends com.steamclock.steamclog.Redactable { *; }
-keep public class * { # All public classes
    public static *; # All public static fields in those classes
    public protected abstract *(...); # All public or protected abstract methods in those classes
}

-keep,allowoptimization class com.steamclock.steamclog.* { *; }