# Room, Compose und kotlinx.serialization liefern eigene Consumer-Regeln mit.
# DTOs aus assets/*.json werden per kotlinx.serialization (Plugin-generierte Serializer) gelesen.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
