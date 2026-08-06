# Add project specific ProGuard rules here.

# 全局: 保留原始类名/方法名, 只做 shrinking + 优化。
# 这避免了 Glance 反射、Room 生成类、Serialization 反射因改名而崩溃。
-dontobfuscate

# ── 通用: 保留注解、内部类、泛型签名、源文件名(堆栈可读)、调试行号 ──
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, SourceFile, LineNumberTable

# ── Kotlinx Serialization ──
# @Serializable companion 的 serializer() 不能被裁; serializer 本身要保留
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keep,includedescriptorclasses class com.lingion.sleepy.**$$serializer { *; }
-keepclassmembers class com.lingion.sleepy.** {
    *** Companion;
}
-keepclasseswithmembers class com.lingion.sleepy.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# ── Room (KSP 生成类, DAO 方法名被反射调用) ──
-keep class com.lingion.sleepy.data.entity.** { *; }
-keep class com.lingion.sleepy.data.dao.** { *; }
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# ── WorkManager (InputMerger 通过反射实例化; ListenableWorker 子类同理) ──
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.ListenableWorker { <init>(...); }

# ── Glance / Compose (RemoteViews 通过反射膨胀, 不能裁) ──
-keep class androidx.glance.** { *; }
-keep class androidx.glance.appwidget.** { *; }
-dontwarn androidx.glance.**
# AppWidgetProvider 子类: 系统通过 Manifest 反射实例化
-keep class * extends android.appwidget.AppWidgetProvider { <init>(); }
# BroadcastReceiver/Activity 子类: 同上
-keep class * extends android.content.BroadcastReceiver { <init>(); }

# ── WakeUp 课表兼容 schema (外部 JSON 反序列化) ──
-keep class com.wakeup.pure.** { *; }

# ── jsoup ──
-dontwarn org.jsoup.**

# ── Coroutines (低层级 internal API) ──
-dontwarn kotlinx.coroutines.**

# ── Coil ──
-dontwarn coil.**
