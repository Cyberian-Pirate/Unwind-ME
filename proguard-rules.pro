# Unwind Launcher: the default Android + Compose rules are enough.
# Keep our own code readable in crash logs.
-keep class com.unwind.launcher.** { *; }
-dontwarn org.jspecify.annotations.**
