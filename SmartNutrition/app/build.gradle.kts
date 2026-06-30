import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.team.smartnutrition"
    compileSdk = 35

    // Đọc cấu hình từ local.properties
    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { localProperties.load(it) }
    }

    defaultConfig {
        applicationId = "com.team.smartnutrition"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val geminiKey = localProperties.getProperty("GEMINI_API_KEY") ?: ""
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("../release-key.jks")
            storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD") ?: ""
            keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS") ?: ""
            keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
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
        buildConfig = true
    }
}

dependencies {
    // ═══════════════════════════════════════════
    // CORE (Dùng chung cho tất cả module)
    // ═══════════════════════════════════════════
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // ═══════════════════════════════════════════
    // JETPACK COMPOSE (Dùng chung)
    // ═══════════════════════════════════════════
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.material) // Material Components (XML themes)
    debugImplementation(libs.androidx.ui.tooling)

    // ═══════════════════════════════════════════
    // NAVIGATION (Dùng chung)
    // ═══════════════════════════════════════════
    implementation(libs.androidx.navigation.compose)

    // ═══════════════════════════════════════════
    // FIREBASE (Module 1: Auth, tất cả: Firestore)
    // ═══════════════════════════════════════════
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    // ═══════════════════════════════════════════
    // GOOGLE SIGN-IN (Module 1)
    // ═══════════════════════════════════════════
    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.googleid)

    // ═══════════════════════════════════════════
    // CAMERAX (Module 2: Pantry Scanner)
    // ═══════════════════════════════════════════
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    // ═══════════════════════════════════════════
    // ML KIT BARCODE (Module 2)
    // ═══════════════════════════════════════════
    implementation(libs.mlkit.barcode)

    // ═══════════════════════════════════════════
    // GEMINI AI (Module 2, 3)
    // ═══════════════════════════════════════════
    implementation(libs.generativeai)

    // ═══════════════════════════════════════════
    // VICO CHARTS (Module 5: Analytics)
    // ═══════════════════════════════════════════
    implementation(libs.vico.compose.m3)

    // ═══════════════════════════════════════════
    // UTILITIES (Dùng chung)
    // ═══════════════════════════════════════════
    implementation(libs.gson)
    implementation(libs.coil.compose)
    implementation(libs.concurrent.futures.ktx)
    implementation(libs.guava)
}
