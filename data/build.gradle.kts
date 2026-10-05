plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// :data — Room database, DAOs, entities and repositories (everything under com.minimart.pos.data).
android {
    namespace = "com.minimart.pos.data"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = false }
}

dependencies {
    api(project(":core"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Types below appear in the public API of repositories/entities, so expose them with `api`.
    api(libs.room.runtime)
    api(libs.room.ktx)
    api(libs.room.paging)
    api(libs.paging.runtime)
    api(libs.datastore.preferences)
    api(libs.coroutines.android)
    ksp(libs.room.compiler)
}
