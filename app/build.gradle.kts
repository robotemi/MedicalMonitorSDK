plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.robo.medicalmonitors.sdkdemo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.robo.medicalmonitors.sdkdemo"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
}

dependencies {
    // 本项目只接入这个 AAR，不引用原工程的任何 module 或源码目录。
   implementation(files("libs/MedicalMonitors_release_v1.0.0.aar"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("com.robotemi:sdk:1.138.0")

    testImplementation("junit:junit:4.13.2")
}
