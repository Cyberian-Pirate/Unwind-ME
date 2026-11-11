plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// A fixed signing key (kept in GitHub Secrets) is what lets each new APK install OVER the old one.
// Without it, every build gets a throwaway key and Android refuses to update the installed app.
val keystorePath: String? = System.getenv("UNWIND_KEYSTORE_PATH")

android {
    namespace = "com.unwind.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.unwind.launcher"
        minSdk = 24
        targetSdk = 34
        // Goes up by one on every GitHub build, so Android always sees the new APK as an update
        versionCode = 100 + (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0)
        versionName = "2.2"
    }

    signingConfigs {
        if (keystorePath != null && file(keystorePath).exists()) {
            create("unwind") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("UNWIND_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("UNWIND_KEY_ALIAS")
                keyPassword = System.getenv("UNWIND_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("unwind")?.let { signingConfig = it }
        }
        release {
            // The release build is much smoother than the debug build, so this is the APK to install.
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("unwind") ?: signingConfigs.getByName("debug")
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    // The DreamService drives its own Compose lifecycle owner (a dream
    // has no Activity), so the lifecycle classes must be on the classpath.
    implementation("androidx.lifecycle:lifecycle-runtime-ktx")
    // Lets Android pre-compile the Compose code on first run, which removes most start-up stutter
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
}
