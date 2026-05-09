# Firebase
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Firestore
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder { *** rewind(); }

# Gson
-keepattributes Signature
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Sticky Models — Gson reflection ile serialize/deserialize ediliyor, obfuscate edilmemeli
-keep class com.sticly.Pack { <fields>; }
-keep class com.sticly.Sticker { <fields>; }
-keep class com.sticly.Response { <fields>; }
-keep class com.sticly.BillingSettings { <fields>; }
-keep class com.sticly.BillingPlan { <fields>; }
-keep class com.sticly.BillingConfig { <fields>; }

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# MediaPipe - R8 optimizer uyumluluğu için gerekli
-keep class com.google.mediapipe.** { *; }
-keep class com.google.mediapipe.framework.** { *; }
-keep class com.google.mediapipe.tasks.** { *; }
-keep class com.google.mediapipe.formats.** { *; }
-keepclassmembers class com.google.mediapipe.** { *; }
-keepclassmembers class com.google.mediapipe.framework.Graph {
    native <methods>;
    static <fields>;
    void <clinit>();
}
-dontwarn com.google.mediapipe.**

# MediaPipe için stack frame koruması (caller lookup için gerekli)
-keepattributes SourceFile,LineNumberTable,EnclosingMethod,InnerClasses

# Protobuf (MediaPipe dependency)
-keep class com.google.protobuf.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { *; }

# Native library loader sınıfları
-keep class com.google.mediapipe.framework.jni.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# Keep data classes
-keep class com.sticly.data.** { *; }
-keep class com.sticly.model.** { *; }

# KRITIK: Ana paket model sınıfları (Pack, Sticker, Response vb.)
-keep class com.sticly.Pack { *; }
-keep class com.sticly.Sticker { *; }
-keep class com.sticly.Response { *; }
-keep class com.sticly.CustomPack { *; }
-keep class com.sticly.CustomSticker { *; }
-keep class com.sticly.BillingSettings { *; }
-keep class com.sticly.BillingConfig { *; }
-keep class com.sticly.BillingPlan { *; }

# KRITIK: StickerProvider - WhatsApp ile iletişim için şart
-keep class com.sticly.StickerProvider { *; }
-keep class * extends android.content.ContentProvider { *; }

# KRITIK: Tüm Kotlin data class'larını koru
-keepclassmembers class com.sticly.** {
    public <init>(...);
    public ** component*();
    public ** copy(...);
}

# AdMob
-keep class com.google.android.gms.ads.** { *; }

# Billing
-keep class com.android.vending.billing.** { *; }
-keep class com.android.billingclient.** { *; }

# FFmpegKit
-keep class com.arthenica.ffmpegkit.** { *; }
-dontwarn com.arthenica.ffmpegkit.**

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Suppress warnings for missing JDK/AutoValue classes
-dontwarn javax.lang.model.**
-dontwarn com.google.auto.value.**
-dontwarn autovalue.shaded.com.squareup.javapoet.**

# gRPC / OkHttp (Firebase BOM 33.x)
-dontwarn com.squareup.okhttp.**
-dontwarn io.grpc.okhttp.**
-keep class io.grpc.** { *; }
-dontwarn io.grpc.**

# Java reflection (Guava)
-dontwarn java.lang.reflect.AnnotatedType
-dontwarn com.google.common.reflect.**

# Shimmer
-keep class com.facebook.shimmer.** { *; }
-dontwarn com.facebook.shimmer.**

# BuildConfig (PocketBase URL'leri için)
-keep class com.sticly.BuildConfig { *; }

# PocketBase / JSON (reflection)
-keepclassmembers class org.json.** { *; }
-keep class org.json.** { *; }

# Lottie
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# uCrop
-keep class com.yalantis.ucrop.** { *; }
-dontwarn com.yalantis.ucrop.**

# PhotoEditor
-keep class com.burhanrashid52.photoeditor.** { *; }
-dontwarn com.burhanrashid52.photoeditor.**
