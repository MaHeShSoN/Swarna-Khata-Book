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

# Keep all model classes that are serialized/deserialized with Firestore
-keep class com.jewelrypos.swarnakhatabook.model.** { *; }
-keepclassmembers class com.jewelrypos.swarnakhatabook.model.** { *; }

# Keep Firestore data classes and their serialization
-keep class com.jewelrypos.swarnakhatabook.DataClasses.** { *; }
-keepclassmembers class com.jewelrypos.swarnakhatabook.DataClasses.** { *; }

# Keep Firestore custom classes
-keep class com.jewelrypos.swarnakhatabook.Enums.** { *; }
-keepclassmembers class com.jewelrypos.swarnakhatabook.Enums.** { *; }

# Keep Repository classes
-keep class com.jewelrypos.swarnakhatabook.Repository.** { *; }
-keepclassmembers class com.jewelrypos.swarnakhatabook.Repository.** { *; }

# Keep ViewModel classes
-keep class com.jewelrypos.swarnakhatabook.ViewModle.** { *; }
-keepclassmembers class com.jewelrypos.swarnakhatabook.ViewModle.** { *; }

# Keep ViewModelFactory classes
-keep class com.jewelrypos.swarnakhatabook.Factorys.** { *; }
-keepclassmembers class com.jewelrypos.swarnakhatabook.Factorys.** { *; }

# Keep Firestore PropertyName annotations
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
}

# Keep serialization-related classes
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Keep Kotlin serialization
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.** { *; }

# Keep collections that might be serialized
-keep class java.util.** { *; }
-keep class kotlin.collections.** { *; }
-keep class kotlin.sequences.** { *; }

# Keep LiveData and other Android Architecture Components
-keep class androidx.lifecycle.** { *; }
-keepclassmembers class androidx.lifecycle.** { *; }

# Keep Coroutines
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembers class kotlin.coroutines.** { *; }

# Keep Navigation Component
-keepnames class androidx.navigation.fragment.NavHostFragment
-keepnames class * extends android.os.Parcelable
-keepnames class * extends java.io.Serializable

# Keep Material Design Components
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**
-dontnote com.google.android.material.**

# Keep AndroidX Components
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-dontwarn androidx.**

# Keep Kotlin-specific classes
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep Serializable implementations
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep KotlinPoet
-keep class com.squareup.kotlinpoet.** { *; }
-keepclassmembers class com.squareup.kotlinpoet.** { *; }
-dontwarn com.squareup.kotlinpoet.**

# Keep Java Annotation Processing
-keep class javax.lang.model.** { *; }
-keep class javax.lang.model.element.** { *; }
-keep class javax.lang.model.type.** { *; }
-keep class javax.lang.model.util.** { *; }
-dontwarn javax.lang.model.**
-dontwarn javax.lang.model.element.**
-dontwarn javax.lang.model.type.**
-dontwarn javax.lang.model.util.**

# Keep Java Annotation API
-keep class javax.annotation.** { *; }
-dontwarn javax.annotation.**

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