plugins {
    id("com.android.application")
}

android {
    namespace = "cloud.kosch.keyswiper"
    compileSdk = 36

    defaultConfig {
        applicationId = "cloud.kosch.keyswiper"
        minSdk = 23
        targetSdk = 36
        versionCode = 5
        versionName = "0.5.0-alpha05"
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

dependencies {
    implementation("com.google.mlkit:language-id:17.0.6")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:digital-ink-recognition:19.0.0")
    testImplementation("junit:junit:4.13.2")
}
