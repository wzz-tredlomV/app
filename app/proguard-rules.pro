# 添加项目特定的 ProGuard 规则
# 参见 https://developer.android.com/studio/build/shrink-code

# 保留 Gson 特定的类
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }

# 保留 Room 数据库组件
-keep class * extends androidx.room.RoomDatabase {
    public static <methods>;
}

# 保留带 @Entity 注解的类
-keep class * {
    @androidx.room.Entity *;
}

# 保留带 @Dao 注解的接口
-keep class * {
    @androidx.room.Dao *;
}

# 保留带 @Database 注解的类
-keep class * {
    @androidx.room.Database *;
}

# 保留 CameraX 组件
-keep class androidx.camera.** { *; }

# 保留 ZXing 组件
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.** { *; }

# 保留协程相关的类
-keep class kotlinx.coroutines.** { *; }

# 保留数据类
-keepclassmembers class * {
    ** toJson();
    ** fromJson(**);
}

# 保留枚举类
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保留本地方法
-keepclasseswithmembernames class * {
    native <methods>;
}

# 保留自定义视图
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

# 保留 Parcelable 实现
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

# 保留 Serializable 实现
-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# 保留注解
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses

# 保留资源类
-keepclassmembers class **.R$* {
    public static <fields>;
}

# 保留数据绑定
-keep class androidx.databinding.** { *; }
-keep class * extends androidx.databinding.ViewDataBinding {
    public static *** inflate(***);
}

# 保留导航组件
-keep class androidx.navigation.** { *; }

# 保留生命周期组件
-keep class androidx.lifecycle.** { *; }

# 保留 WorkManager
-keep class androidx.work.** { *; }

# 防止混淆 EventBus
-keepattributes *Annotation*
-keepclassmembers class * {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }

# 防止混淆 Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.resource.bitmap.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}

# 通用优化
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify
-dontwarn android.support.**
-dontwarn com.google.android.material.**
-dontwarn org.jetbrains.**
-dontwarn androidx.**
-dontwarn com.google.**
-dontwarn sun.misc.**
-dontwarn java.lang.invoke.**
-dontwarn kotlin.**

# 日志
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}
