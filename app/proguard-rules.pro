# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Media3 / ExoPlayer
-dontwarn androidx.media3.**
-keep class androidx.media3.exoplayer.** { *; }

# Coil
-dontwarn coil.**

# Kotlin metadata used by DataStore / Room reflection-free paths
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisible*Annotations

# Compose
-dontwarn androidx.compose.**
