plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.xrworkspace.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.xrworkspace.app"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    buildFeatures {
        compose = true
        aidl = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)

    // moonlight-core module
    implementation(project(":moonlight-core"))

    // Jetpack XR
    implementation(libs.androidx.arcore)
    implementation(libs.androidx.scenecore)
    implementation(libs.androidx.compose.xr)
    implementation(libs.kotlinx.coroutines.guava)
    compileOnly(libs.androidx.extensions.xr)

    // Compose
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.adaptive.android)
    implementation(libs.androidx.concurrent.futures)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.material)

    // jmDNS — needed at compile time because DiscoveryManager instantiates JmDNSDiscoveryAgent
    // which extends javax.jmdns.ServiceListener (a transitive dep of moonlight-core)
    implementation("org.jmdns:jmdns:3.5.9")

    // OkHttp — for SunshineApiManager (Sunshine uses digest auth, needs OkHttp Authenticator)
    // moonlight-core uses OkHttp transitively but as `implementation` (not `api`), so we
    // must declare it directly here to use it in app-module source files.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Unit testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
