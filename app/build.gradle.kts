plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.aerogcsclone"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.aerogcsclone"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}
dependencies {
    // Core + lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose BOM (manages versions automatically)
    implementation(platform(libs.androidx.compose.bom))
    implementation("com.google.android.gms:play-services-maps:19.2.0")

    // Core Maps SDK (version will be managed by the BOM)
//    implementation("com.google.android.gms:play-services-maps")
    implementation("com.google.maps.android:maps-compose:4.4.2") // You had 4.4.2, which is good.

    // Maps Utils (for clustering, GeoJSON, KML, heatmaps, etc.)
    implementation("com.google.maps.android:android-maps-utils:3.8.2")

    // Compose UI
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)

    // Material3 (only one source, from libs)
    implementation(libs.androidx.material3)

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.9.3")

    // Material Icons Extended (choose one approach → using BOM-managed one)
    implementation("androidx.compose.material:material-icons-extended")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
dependencies {
    // MAVLink message definitions (standard dialects like common.xml)
    implementation("com.divpundir.mavlink:definitions:1.2.8")


    // TCP connection client
    implementation("com.divpundir.mavlink:connection-tcp:1.2.8")


    // Coroutines adapter (recommended for Android)
    implementation("com.divpundir.mavlink:adapter-coroutines:1.2.8")
}


