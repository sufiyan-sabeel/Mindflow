# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.mindflow.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.mindflow.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
