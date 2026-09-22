# Device Signals SDK — R8/ProGuard Rules

# Keep EncryptedSharedPreferences (reflection-based)
-keep class androidx.security.crypto.** { *; }

# Keep Play Integrity classes accessed via reflection
-dontwarn com.google.android.play.core.integrity.**
-keep class com.google.android.play.core.integrity.** { *; }
