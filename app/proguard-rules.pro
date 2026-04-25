# ── Room ──────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * extends androidx.room.RoomDatabase { abstract *; }
-keep interface * extends androidx.room.RoomDatabase

# ── Hilt ──────────────────────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keepclasseswithmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}
-keep @dagger.hilt.android.AndroidEntryPoint class *

# ── DataStore ─────────────────────────────────────────────────────────────────
-keep class androidx.datastore.** { *; }
-keepclassmembers class * implements kotlinx.serialization.KSerializer { *; }

# ── Kotlin coroutines ─────────────────────────────────────────────────────────
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ── WorkManager + Hilt Worker ─────────────────────────────────────────────────
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keepclassmembers class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── Biometric ─────────────────────────────────────────────────────────────────
-keep class androidx.biometric.** { *; }

# ── MLKit barcode ─────────────────────────────────────────────────────────────
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }

# ── CameraX ───────────────────────────────────────────────────────────────────
-keep class androidx.camera.** { *; }

# ── Entities & DAOs (prevent field name obfuscation) ─────────────────────────
-keep class com.minimart.pos.data.entity.** { *; }
-keep class com.minimart.pos.data.dao.** { *; }
-keep class com.minimart.pos.data.db.** { *; }

# ── General ───────────────────────────────────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn kotlin.**
-dontwarn kotlinx.**
