# R8 / ProGuard rules for release builds (minify + shrinkResources are enabled).

-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations, EnclosingMethod

# kotlinx.serialization: keep generated serializers for app models (Supabase decodes these reflectively).
-keepclassmembers class com.crispyburger.admin.** {
    *** Companion;
}
-keepclasseswithmembers class com.crispyburger.admin.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.crispyburger.admin.**$$serializer { *; }
-dontnote kotlinx.serialization.**

# Ktor / OkHttp optional dependencies that are not present on Android.
-dontwarn java.lang.management.**
-dontwarn org.slf4j.**
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
