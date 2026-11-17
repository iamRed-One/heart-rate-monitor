plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.myapplication"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.ambali.heart"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlinx.**")
            }
        }
    }
    sourceSets {
        getByName("main") { jniLibs.srcDir("libs")}
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))
    implementation("com.squareup.okhttp3:okhttp:4.9.0")
    implementation("com.google.code.gson:gson:2.10.1")
    // Veepoo SDK (ET488 watch) transitive deps
    implementation("no.nordicsemi.android.support.v18:scanner:1.4.2")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    // Nordic mcumgr — Veepoo inits its OTA manager on connect; without these it crashes with
    // NoClassDefFoundError io.runtime.mcumgr.ble.McuMgrBleTransport.
    implementation("no.nordicsemi.android:mcumgr-core:2.7.4")
    implementation("no.nordicsemi.android:mcumgr-ble:2.7.4")
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.1.0")
    implementation("no.nordicsemi.android:dfu:1.10.1")
    implementation("com.alibaba:fastjson:1.2.83")
    implementation("org.apache.commons:commons-text:1.9")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
