// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.9.0" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id ("androidx.navigation.safeargs.kotlin") version "2.8.9" apply false
    id("com.google.firebase.crashlytics") version "2.9.9" apply false
}
// Project-level build.gradle
buildscript {
    dependencies {
        classpath("com.google.gms:google-services:4.4.1")
    }
}