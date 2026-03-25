plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.plugin.serialization)
}

android {
    namespace = "com.saltichash.musicapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.saltichash.musicapp"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.youtubedl.core)
    implementation(libs.youtubedl.ffmpeg)
    implementation(libs.youtubedl.aria2c)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.legacy.support.v4)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.preference)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}