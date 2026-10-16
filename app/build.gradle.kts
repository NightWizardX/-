import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI numbers each build so a newer APK always installs as an update of the older one.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

// The release key never lives in the repo: CI decodes it from GitHub Actions secrets
// (see README) and passes its location and password through the environment.
val releaseKeystore: String? = System.getenv("SIGNING_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }

android {
    namespace = "com.nwx.expensetracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nwx.expensetracker"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
        // Phones are ARM; leaving out x86 builds of the OCR library keeps the APK smaller.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        releaseKeystore?.let { path ->
            // trim(): a password pasted into GitHub Secrets easily picks up a trailing newline.
            val storePass = System.getenv("SIGNING_STORE_PASSWORD")?.trim()
            create("release") {
                storeFile = file(path)
                storePassword = storePass
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")?.trim() ?: "expense-tracker"
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")?.trim() ?: storePass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Without the release key (local builds, or CI before the secrets are added) use the
            // machine's debug key so the APK still installs. Such an APK can't update, or be
            // updated by, one signed with the release key.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Reading bank slips on the device: OCR (Thai + English traineddata in assets/tessdata) and the slip QR.
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
}
