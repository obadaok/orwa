# Keep line number information for debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# Media3 — session and notification provider rely on reflection.
-keep class androidx.media3.session.** { *; }
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-keepclassmembers class com.urwah.dhikr.audio.** { *; }

# Keep Kotlin coroutines.
-keep class kotlinx.coroutines.** { *; }