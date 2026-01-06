# Vanta ProGuard Rules

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Vanta model classes for serialization
-keep,includedescriptorclasses class com.vanta.data.network.**$$serializer { *; }
-keepclassmembers class com.vanta.data.network.** {
    *** Companion;
}
-keepclasseswithmembers class com.vanta.data.network.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# ML Kit
-keep class com.google.mlkit.** { *; }
