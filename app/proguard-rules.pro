# Add project specific ProGuard rules here.
-dontobfuscate
-keep class com.wakeup.pure.** { *; }
-keep class kotlinx.serialization.** { *; }
-keepattributes *Annotation*, InnerClasses