# --- Data Models for Firebase Realtime Database & Gson ---
# Keep model fields and no-arg constructors intact for Firebase getValue() & Gson deserialization
-keepclassmembers class com.albasel.tawsilaapp.data.model.** {
    <fields>;
    <init>(...);
}
-keep class com.albasel.tawsilaapp.data.model.** { *; }

# --- Android Manifest Services & Components & Notification Helpers ---
-keep class com.albasel.tawsilaapp.util.FCMV1Helper { *; }
-keep class com.albasel.tawsilaapp.util.GoogleDriveHelper { *; }
-keep class com.albasel.tawsilaapp.util.MyFirebaseMessagingService { *; }
-keep class com.albasel.tawsilaapp.service.LocationForegroundService { *; }

# Keep R$raw inner class to prevent resource identifier stripping
-keep class com.albasel.tawsilaapp.R$raw { *; }
-keepclassmembers class com.albasel.tawsilaapp.R$raw { *; }

# --- Firebase & Google Play Services & Auth ---
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class com.google.auth.** { *; }

# --- OkHttp & Gson ---
-keep class okhttp3.** { *; }
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.code.gson.annotations.SerializedName <fields>;
}

# --- Strip Log.d, Log.v, Log.i in Release Build ---
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# --- Suppress third-party missing class warnings ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn com.google.auth.**
-dontwarn com.google.api.client.**
-dontwarn javax.naming.**
-dontwarn org.apache.commons.logging.**

# --- Coil & Coroutines ---
-keep class coil.** { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# --- Essential attributes for Reflection, Navigation, and Serialization ---
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
