# ==============================================================================
# SUIKAPLAYER - PROGUARD / R8 RULES
# ==============================================================================

# Keep line numbers for crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep generic attributes
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# ==============================================================================
# ROOM DATABASE
# ==============================================================================
-keep class com.demonlab.suikaplayer.data.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}

# ==============================================================================
# GSON & DATA CLASSES (Cache & Backup)
# ==============================================================================
-keep class com.demonlab.suikaplayer.tools.Song { *; }
-keep class com.demonlab.suikaplayer.tools.PlaylistExportData { *; }
-keep class com.demonlab.suikaplayer.tools.PlaylistData { *; }
-keep class com.demonlab.suikaplayer.tools.SongMetadata { *; }

-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ==============================================================================
# TOOLS (PlaybackManager, MusicService, SettingsManager, audio pipeline)
# ==============================================================================
-keep class com.demonlab.suikaplayer.tools.** { *; }

# ==============================================================================
# AUDIO EFFECTS (reflection in DynamicsEffect)
# ==============================================================================
-keep class com.demonlab.suikaplayer.audio.** { *; }

# ==============================================================================
# UI ACTIVITIES (EqualizerActivity, etc. — Compose state)
# ==============================================================================
-keep class com.demonlab.suikaplayer.ui.** { *; }

# ==============================================================================
# JAUDIOTAGGER (Metadata extraction)
# ==============================================================================
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**

# ==============================================================================
# KOTLIN COROUTINES
# ==============================================================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ==============================================================================
# OKIO / OKHTTP (transitive deps via Coil)
# ==============================================================================
-dontwarn okio.**
-dontwarn okhttp3.**
