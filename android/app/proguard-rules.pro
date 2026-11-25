# Keep Kotlinx Serialization models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.jzb.tower.core.model.**$$serializer { *; }
-keepclassmembers class com.jzb.tower.core.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.jzb.tower.core.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep SnakeYAML reflection classes
-dontwarn org.yaml.snakeyaml.**
-keep class org.yaml.snakeyaml.** { *; }
