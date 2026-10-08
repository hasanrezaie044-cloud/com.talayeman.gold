# Keep Room entities
-keep class com.talayeman.gold.data.local.entity.** { *; }

# Keep Gson / Retrofit models
-keep class com.talayeman.gold.data.remote.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
