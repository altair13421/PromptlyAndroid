# Promptly ProGuard rules

# --- Kotlin Metadata ---
-keep class kotlin.Metadata { *; }

# --- Kotlinx Serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.promptly.app.**$$serializer { *; }
-keepclassmembers class com.promptly.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.promptly.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Room ---
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# --- Hilt ---
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# --- Compose ---
-dontwarn androidx.compose.**

# --- WorkManager ---
-keep class androidx.work.** { *; }
