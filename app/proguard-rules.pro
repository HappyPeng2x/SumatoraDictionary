# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keep class ch.qos.** { *; }
-keep class org.slf4j.** { *; }
-keepattributes *Annotation*
-dontwarn ch.qos.logback.core.net.*

# Jackson 2.11 does not bundle R8 rules; keep all Jackson classes to prevent
# reflection-based deserialization from breaking under minification.
-keep class com.fasterxml.jackson.core.** { *; }
-keep class com.fasterxml.jackson.databind.** { *; }
-keep class com.fasterxml.jackson.annotation.** { *; }
-dontwarn com.fasterxml.jackson.**

# Keep Bookmark model so R8 does not strip fields accessed only via Jackson reflection.
-keep class org.happypeng.sumatora.core.bookmark.Bookmark { *; }

# Room instantiates its generated *_Impl database classes by reflection (Room.databaseBuilder ->
# Class.forName(...).getDeclaredConstructor()), including androidx.work's internal WorkDatabase -
# with nothing in app code referencing the generated class by name, R8 can strip its no-arg
# constructor as unused. Broke release-build launch entirely (crashed in Application.onCreate
# with "NoSuchMethodException: androidx.work.impl.WorkDatabase_Impl.<init>") before this was added.
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>(...);
}
