# ProGuard & R8 Optimization Rules for SMS Backup

# 1. OkHttp 3 & 4
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# 2. Google Play Services Auth
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.** { *; }
-keep interface com.google.android.gms.common.** { *; }
-dontwarn com.google.android.gms.**

# 3. WorkManager (preserve worker reflection constructors)
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.personal.msgforwarder.worker.** { *; }

# 4. Broadcast Receivers & Services
-keep class com.personal.msgforwarder.receiver.** { *; }
-keep class com.personal.msgforwarder.service.** { *; }

# 5. Data Models
-keep class com.personal.msgforwarder.data.MessageData { *; }
-keep class com.personal.msgforwarder.data.PreferencesHelper { *; }

# 6. Kotlin Coroutines & Dispatchers
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**
