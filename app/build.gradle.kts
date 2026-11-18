plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.pharos.app"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "org.pharos.mesh"
        minSdk = 23
        targetSdk = 36
        versionCode = 20261117
        versionName = "1.0.0-NIGHT-OPS"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("${rootDir}/pharos-release.jks")
            storePassword = System.getenv("STORE_PASSWORD") ?: "pharos2026"
            keyAlias = System.getenv("KEY_ALIAS") ?: "pharos_release"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "pharos2026"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
        getByName("debug") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("playStore") {
            dimension = "distribution"
            buildConfigField("boolean", "IS_PLAY_STORE_FLAVOR", "true")
            buildConfigField("String", "DISTRIBUTION_CHANNEL", "\"GOOGLE_PLAY_AAB\"")
        }
        create("standalone") {
            dimension = "distribution"
            buildConfigField("boolean", "IS_PLAY_STORE_FLAVOR", "false")
            buildConfigField("String", "DISTRIBUTION_CHANNEL", "\"STANDALONE_UNIVERSAL_APK\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            // Required for 16 KB page size compliance when loading uncompressed shared libraries
            useLegacyPackaging = false
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }
}

dependencies {
    implementation(project(":core:protocol"))
    implementation(project(":core:crypto"))
    implementation(project(":core:storage"))
    implementation(project(":core:optical"))
    implementation(project(":core:mesh"))
    implementation(project(":core:audio"))
    implementation(project(":core:offline-web-bridge"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
