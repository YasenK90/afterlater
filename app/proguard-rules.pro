# Keep Room and WorkManager names stable for readable stack traces and
# because WorkManager restores persisted worker class names by string.
-keepnames class com.afterlater.core.database.** { *; }
-keepnames class com.afterlater.worker.** { *; }

# kotlinx.serialization under R8 full mode: keep the generated serializers
# and companions for the backup codec's @Serializable classes, or restoring
# a backup fails at parse time.
-keep,includedescriptorclasses class com.afterlater.**$$serializer { *; }
-keepclassmembers class com.afterlater.** {
    *** Companion;
}
-keepclasseswithmembers class com.afterlater.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Beta testers report stack traces by hand; keep line numbers readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
