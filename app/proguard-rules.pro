# Keep Room generated DAO implementations and type converters
-keep class androidx.room.** { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# osmdroid uses reflection in several places (tile providers, etc.)
-keep class org.osmdroid.** { *; }
-keep class org.osmdroid.library.R$* { *; }
-dontwarn org.osmdroid.**

# Kotlin reflection (used by coroutines/Compose under release)
-dontwarn kotlin.reflect.**
-keep class kotlin.Metadata { *; }

# Compose tooling preview class
-keep class androidx.compose.ui.tooling.** { *; }
