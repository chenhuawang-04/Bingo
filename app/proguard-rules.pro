# Add project specific ProGuard rules here.
-keepattributes *Annotation*

# Moshi
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep class com.xty.englishhelper.data.remote.dto.** { *; }
-keep class com.xty.englishhelper.data.json.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
