plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// :data — Room database, DAOs, entities and repositories (everything under com.minimart.pos.data).
android {
    namespace = "com.minimart.pos.data"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    // Lint is enforced: errors fail the build (CI runs `lintDebug`). Warnings are reported but do not fail it.
    lint {
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = false
    }
    testOptions {
        // Robolectric needs Android resources/manifest merged for the in-memory Room tests.
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    api(project(":core"))

    implementation(libs.hilt.android)
    // Native Argon2id for PinHasher (an Android library, so it cannot live in the pure-JVM :core).
    implementation("com.lambdapioneer.argon2kt:argon2kt:1.4.0")
    ksp(libs.hilt.compiler)

    // Types below appear in the public API of repositories/entities, so expose them with `api`.
    api(libs.room.runtime)
    api(libs.room.ktx)
    api(libs.room.paging)
    api(libs.paging.runtime)
    api(libs.datastore.preferences)
    api(libs.coroutines.android)
    ksp(libs.room.compiler)

    // JVM tests for the data layer: real Room (in-memory, via Robolectric) so DAO SQL and constraints are exercised.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
