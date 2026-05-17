# --------------------------------------------------------------------------------
# 核心瘦身：忽略 Ktor 在 Android 上找不到 ManagementFactory 的无关报错
# --------------------------------------------------------------------------------
-dontwarn java.lang.management.**
-dontwarn javax.management.**

# 保持 Compose 混淆规则
-keepattributes Signature
-keepattributes *Annotation*
-keep class kotlin.reflect.jvm.internal.** { *; }

# Supabase / Ktor / Serialization 规则
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-keep class kotlinx.serialization.json.** { *; }
-keepattributes EnclosingMethod,InnerClasses

# 我们的数据模型类绝对不能混淆字段名（核心，否则 Supabase 会存入乱码）
-keepclassmembers class com.lvhonyua.apptrack.data.** {
    @kotlinx.serialization.SerialName <fields>;
}
-keep class com.lvhonyua.apptrack.data.** { *; }

# 针对 OkHttp 的混淆优化
-dontwarn okhttp3.internal.platform.ConscryptPlatform
