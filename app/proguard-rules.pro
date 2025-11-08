# AI Secretary - ProGuard Rules

# Keep line numbers for debugging stack traces
-keepattributes SourceFile,LineNumberTable

# Keep source file names
-renamesourcefileattribute SourceFile

# Keep custom exceptions
-keep public class * extends java.lang.Exception

# Keep application classes
-keep class com.aisecretary.taskmaster.** { *; }

# Keep database entities and DAOs
-keep class com.aisecretary.taskmaster.database.** { *; }
-keepclassmembers class com.aisecretary.taskmaster.database.** { *; }

# Keep repository
-keep class com.aisecretary.taskmaster.repository.** { *; }

# Keep parcelable classes
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}

# Keep serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# AndroidX
-keep class androidx.** { *; }
-dontwarn androidx.**

# Material Components
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# Keep widget providers
-keep class * extends android.appwidget.AppWidgetProvider {
    <init>(...);
}

# Keep broadcast receivers
-keep class * extends android.content.BroadcastReceiver {
    <init>(...);
}

# Keep services
-keep class * extends android.app.Service {
    <init>(...);
}

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep view constructors (for XML inflation)
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
