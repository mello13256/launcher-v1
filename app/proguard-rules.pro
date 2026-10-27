# kotlinx.serialization: keep generated serializers of the config model.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.hexa.core.** {
    *** Companion;
}
-keepclasseswithmembers class com.hexa.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.hexa.core.**$$serializer { *; }

# Notification listener is instantiated by the system.
-keep class com.hexa.launcher.media.MediaListenerService
