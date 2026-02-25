# EdgeClaw ProGuard Rules

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep EdgeClaw data classes
-keep class com.edgeclaw.mobile.core.model.** { *; }
-keep class com.edgeclaw.mobile.core.engine.** { *; }

# Keep JNI native methods (future Rust FFI)
-keepclasseswithmembernames class * {
    native <methods>;
}
