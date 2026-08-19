# Add project specific ProGuard rules here.

# ── General Android ───────────────────────────────────────────────────────────
# Preserve source file names and line numbers in stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*
-keepattributes Signature

# Keep custom exceptions
-keep public class * extends java.lang.Exception

# Parcelable
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

# Enums
-keepclassmembers enum * {
  public static **[] values();
  public static ** valueOf(java.lang.String);
}

# Native methods
-keepclasseswithmembernames class * {
  native <methods>;
}

# Kotlin coroutines
-dontwarn kotlinx.coroutines.**

# Compose
-dontwarn androidx.compose.**
