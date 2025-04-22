plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "2.1.0-1.0.29"
    id("com.google.gms.google-services")
    id("androidx.navigation.safeargs.kotlin")
}

android {
    namespace = "com.jewelrypos.swarnakhatabook"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jewelrypos.swarnakhatabook"
        minSdk = 26
        targetSdk = 34
        versionCode = 12
        versionName = "1.12"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        dataBinding = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.privacysandbox.tools:tools-core:1.0.0-alpha12")

    //Testing
    implementation(kotlin("test"))
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.test:runner:1.5.2")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:3.12.4")
    testImplementation("org.robolectric:robolectric:4.8")
    testImplementation("androidx.fragment:fragment-testing:1.8.6")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.mockito:mockito-inline:3.12.4")

    // Firebase dependencies
    implementation(platform("com.google.firebase:firebase-bom:32.7.3"))
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")

    implementation("androidx.room:room-runtime:2.6.1") // Room
    ksp("androidx.room:room-compiler:2.6.1") // Room annotation processor
    implementation("androidx.room:room-ktx:2.6.1")//kotlin extentions for room


    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3") // Or latest version

    // Google Play In-App Updates (replacement for deprecated Play Core)
    implementation("com.google.android.play:app-update:2.1.0")
    implementation("com.google.android.play:app-update-ktx:2.1.0")

    // Firebase Remote Config (you already have Firebase BOM)
    implementation("com.google.firebase:firebase-config-ktx")

    // Coroutines for Play Services (you already have base coroutines)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")


    //Signature
    implementation("com.github.gcacace:signature-pad:1.3.1")

    //Qr Code
    implementation("com.google.zxing:core:3.4.1")

    //For Charts
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    //For tab View
    implementation("androidx.viewpager2:viewpager2:1.1.0")

    //For Image View
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // Navigation Component
    implementation("androidx.navigation:navigation-fragment-ktx:2.8.5")
    implementation("androidx.navigation:navigation-ui-ktx:2.8.5")

    //Pdf
    implementation("com.github.mhiew:android-pdf-viewer:3.2.0-beta.3")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    implementation("com.github.mukeshsolanki.android-otpview-pinview:otpview:3.2.0")

    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    implementation("nl.joery.animatedbottombar:library:1.1.0")

    implementation("com.google.code.gson:gson:2.10.1")

    implementation("androidx.biometric:biometric-ktx:1.4.0-alpha02")

    implementation("com.android.billingclient:billing:6.0.1")
    implementation("com.android.billingclient:billing-ktx:6.0.1")

    implementation("org.apache.poi:poi:5.2.3")
    implementation("org.apache.poi:poi-ooxml:5.2.3")
    implementation("androidx.gridlayout:gridlayout:1.1.0")


}