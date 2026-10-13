# =========================================================================
# Zenith CashFlow - Production ProGuard & R8 Optimization Rules
# =========================================================================

# --- General Optimization & Stack Trace Preservation ---
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# --- AndroidX & Jetpack Compose ---
-dontwarn androidx.**
-keep class androidx.compose.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.navigation.** { *; }

# --- Kotlin Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# --- Kotlinx Serialization ---
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keepclassmembers class **$$serializer {
    *;
}
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <init>(...);
}

# --- Supabase & Ktor Client ---
-keep class io.github.jan.supabase.** { *; }
-keep interface io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-keep interface io.ktor.** { *; }
-dontwarn io.ktor.**

# --- Room Database ---
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}

# --- Moshi & Retrofit ---
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <methods>;
}
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <fields>;
}
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <init>(...);
}
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# --- Domain & Data Models (Preserve all fields for Room / JSON) ---
-keep class com.example.data.models.** { *; }
-keep class com.example.data.familyledger.** { *; }
-keep class com.example.data.network.** { *; }
-keep class com.example.data.ai.** { *; }
-keep class com.example.data.upi.** { *; }

# --- Google ML Kit & Code Scanner ---
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.vision.** { *; }
-dontwarn com.google.mlkit.**

# --- ZXing Barcode Library ---
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# --- CameraX ---
-dontwarn androidx.camera.**
-keep class androidx.camera.** { *; }
