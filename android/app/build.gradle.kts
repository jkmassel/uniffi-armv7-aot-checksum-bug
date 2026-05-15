plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.repro"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.uniffireproaot"
        minSdk = 24
        targetSdk = 30
        versionCode = 1
        versionName = "1.0"

        // Ship only armeabi-v7a so Android forks a 32-bit process for the app
        // on any device that supports armeabi-v7a in abilist32.
        ndk { abiFilters += "armeabi-v7a" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging { jniLibs.useLegacyPackaging = true }
}

dependencies {
    // JNA's Android AAR ships libjnidispatch.so alongside the Java classes.
    // UniFFI-generated Kotlin bindings use JNA's Native.register direct mapping.
    implementation("net.java.dev.jna:jna:5.18.1@aar")
}
