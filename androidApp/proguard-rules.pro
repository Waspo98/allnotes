# Keep kotlinx.serialization generated serializers and Companion objects
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.overbay.allnotes.model.**$$serializer { *; }
-keepclassmembers class app.overbay.allnotes.model.** {
    *** Companion;
}
-keepclasseswithmembers class app.overbay.allnotes.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep SQLDelight database implementation & adapters
-keep class app.overbay.allnotes.db.** { *; }
-keep class app.cash.sqldelight.** { *; }
