plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.minimart.pos"
    compileSdk = 35

    signingConfigs {
        create("release") {
            // No default passwords: the release keystore and its secrets come from the environment
            // (GitHub Actions secrets in CI, see docs/RELEASE_SIGNING.md). Debug builds never need them.
            storeFile     = file(System.getenv("SIGNING_KEYSTORE_PATH") ?: "minimart-release.jks")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias      = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword   = System.getenv("SIGNING_KEY_PASSWORD")
        }
    }

    // Building a release without the signing secrets must fail loudly, not sign with a throwaway key.
    gradle.taskGraph.whenReady {
        val wantsRelease = allTasks.any { it.project == project && it.name in setOf("assembleRelease", "bundleRelease", "packageRelease") }
        if (wantsRelease) {
            val missing = listOf("SIGNING_STORE_PASSWORD", "SIGNING_KEY_ALIAS", "SIGNING_KEY_PASSWORD").filter { System.getenv(it).isNullOrBlank() }
            if (missing.isNotEmpty()) throw GradleException("Release signing needs ${missing.joinToString()} (see docs/RELEASE_SIGNING.md)")
            if (!file(System.getenv("SIGNING_KEYSTORE_PATH") ?: "minimart-release.jks").exists())
                throw GradleException("Release keystore not found (see docs/RELEASE_SIGNING.md)")
        }
    }

    defaultConfig {
        applicationId = "com.minimart.pos"
        minSdk = 26
        targetSdk = 35
        versionCode = 117
        versionName = "1.19.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }

    splits {
        abi {
            isEnable = true
            reset()
            // arm64 for modern phones, armeabi-v7a for the many older/budget 32-bit phones, x86_64 for
            // emulators and Chromebooks. The universal APK works on all of them (largest download).
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    lint {
        // Enforced: lint errors fail CI (see .github/workflows/ci.yml) and release builds.
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = true
        // Print the full findings to the build log (the console otherwise shows only the first error).
        textReport = true
        textOutput = file("stdout")
    }
}

dependencies {
    // Installs the baseline profile (src/main/baseline-prof.txt) on first launch, also for sideloaded APKs.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    // Room
    implementation(libs.biometric)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)

    // DataStore
    implementation(libs.datastore.preferences)
    // ML Kit + CameraX
    implementation(libs.mlkit.barcode)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    // Retrofit
    // Coroutines
    implementation(libs.coroutines.android)
    // WorkManager
    implementation(libs.work.runtime)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
    // Splash
    implementation(libs.splashscreen)
    // Coil
    implementation(libs.coil.compose)
    // Accompanist
    implementation(libs.accompanist.permissions)
    // Gson for sync serialization
    implementation("com.google.code.gson:gson:2.10.1")
    // Argon2id PIN hashing
    implementation("com.lambdapioneer.argon2kt:argon2kt:1.4.0")

    debugImplementation(libs.androidx.ui.tooling.preview)

    // Compose UI tests that run on a device/emulator (app/src/androidTest).
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Unit tests for financial calculation logic (Money, CartUiState, PluDecoder).
    // JUnit4 is the standard, long-stable test framework for local JVM unit tests
    // (app/src/test) — no Android framework or emulator needed since the classes
    // under test (Money, CartUiState's computed properties, PluDecoder) are pure
    // Kotlin/JVM logic with no Android dependencies.
    testImplementation("junit:junit:4.13.2")
    // org.json for JVM tests (Android's own copy is only a stub there)
    testImplementation("org.json:json:20240303")

    // MIGRATION ONLY: reads the old EncryptedSharedPreferences files once (LegacySecurePrefsMigrator).
    // New secrets use KeystorePrefs (AES-256-GCM, Android Keystore). Delete this line and the migrator
    // once most phones have updated.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
