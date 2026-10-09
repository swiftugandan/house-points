# kotlinx.serialization: keep generated serializers for payloads and navigation routes.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class dev.housepoints.** {
    *** Companion;
}
-keepclasseswithmembers class dev.housepoints.** {
    kotlinx.serialization.KSerializer serializer(...);
}
