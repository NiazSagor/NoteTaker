plugins {
    alias(libs.plugins.android.application)
//    alias(libs.plugins.android.library) // This was commented out, assuming it's not needed or managed elsewhere
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.dagger.hilt)
    id("com.google.devtools.ksp") version "2.0.20-1.0.25" // Explicitly apply KSP here
}

android {
    namespace = "com.example.notetaker.feature.workspace"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

//    kotlin {
//        jvmToolchain(17)
//    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
//    implementation(libs.androidx.hilt.navigation-compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
