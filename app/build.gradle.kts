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
        versionCode = 25
        versionName = "0.24.0-alpha25"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Production signing credentials are injected from GitHub Actions Secrets.
    // Never use the ephemeral CI debug key for distributed APKs.
    signingConfigs {
        System.getenv("KEYSWIPER_RELEASE_STORE_PATH")?.takeIf { it.isNotBlank() }?.let { path ->
            create("keyswiperProduction") {
                storeFile = file(path)
                storePassword = System.getenv("KEYSWIPER_RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("KEYSWIPER_RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("KEYSWIPER_RELEASE_KEY_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("keyswiperProduction")?.let { signingConfig = it }
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // A separately installable, NON-PRODUCTION diagnostic variant. It does not
        // replace or erase the user's existing encrypted clipboard history.
        create("installCheck") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".installcheck"
            versionNameSuffix = "-installcheck"
            isDebuggable = false
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
}
