@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "cloud.kosch.keyswiper"
    compileSdk = 36

    defaultConfig {
        applicationId = "cloud.kosch.keyswiper"
        minSdk = 24
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 16
        versionName = "0.16.0-alpha16"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("com.google.mlkit:language-id:17.0.6")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:digital-ink-recognition:19.0.0")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.17.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}
