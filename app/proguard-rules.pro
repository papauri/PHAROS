# PHAROS R8 / ProGuard rules
-keep class org.pharos.core.protocol.** { *; }
-keep class org.pharos.core.crypto.** { *; }
-keep class org.pharos.core.storage.** { *; }
-keep class org.pharos.core.optical.** { *; }
-keep class org.pharos.core.mesh.** { *; }
-keep class org.pharos.core.audio.** { *; }
-keep class org.pharos.core.webbridge.** { *; }
-keep class org.pharos.app.** { *; }

# Keep JNI native methods across all modules
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
