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

# AdMob
-keep class com.google.android.gms.ads.** { *; }

# Billing
-keep class com.android.vending.billing.** { *; }
-keep class com.android.billingclient.** { *; }

# Suppress warnings for missing JDK/AutoValue classes
-dontwarn javax.lang.model.**
-dontwarn com.google.auto.value.**
-dontwarn autovalue.shaded.com.squareup.javapoet.**
