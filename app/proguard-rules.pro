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
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# Keep the Firebase classes
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Keep Room database classes
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep class * extends androidx.room.Dao

# Rules for Picasso
-dontwarn com.squareup.okhttp.**
-dontwarn okio.**
-keep class com.squareup.picasso.** { *; }

# Rules for OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# Rules for MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# Rules for Signature Pad
-keep class com.github.gcacace.signaturepad.** { *; }

# Rules for ZXing
-keep class com.google.zxing.** { *; }

# Rules for Apache POI
-dontwarn org.apache.poi.**
-keep class org.apache.poi.** { *; }

# Rules for PDF libraries
-keep class com.github.barteksc.** { *; }
-keep class com.shockwave.** { *; }
-keep class com.tom_roush.pdfbox.** { *; }

# Rules for all model classes
# Keep all model classes that are serialized/deserialized with Gson
-keepclassmembers class com.jewelrypos.swarnakhatabook.model.** { *; }

# Enable R8 full mode
-allowaccessmodification
-repackageclasses

# Rules for missing classes mentioned in error
-dontwarn com.gemalto.jp2.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn com.graphbuilder.curve.**

# Additional rules for PDFBox
-dontwarn com.tom_roush.pdfbox.filter.**
-keep class com.tom_roush.pdfbox.** { *; }