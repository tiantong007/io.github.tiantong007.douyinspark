-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list

-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, AnnotationDefault


# ---- Rhino 脚本引擎：release 必须整包保留 ----
# Rhino 大量使用反射与按类名查找，被 R8 重命名 / 裁剪后脚本执行会直接失败
# （症状：模板预览报「预览失败：<混淆后的类名>」）。
-keep class org.mozilla.javascript.** { *; }
-keepclassmembers class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**

# 脚本上下文 ctx.* 与引擎自身的类会被 JS 按名字访问
-keep class com.worldmargin.autospark.app.script.** { *; }
-keepclassmembers class com.worldmargin.autospark.app.script.** { *; }

# Rhino 内部有一条 JSON 转换路径引用 java.beans.*（Android 上没有这些类），
# R8 的 missing class 检查会因此中断构建；那条路径在 Android 上不会执行。
-dontwarn java.beans.**

# ---- WebView 的 JS 桥 ----
# @JavascriptInterface 的方法由网页按名字调用，R8 改名 / 删除后 JS 回调进不来。
# 本版编辑器已改成原生文本框，这里保留以防以后再加 WebView。
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
