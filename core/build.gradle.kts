plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// :core — pure, dependency-light building blocks shared by every module:
// Money (cent-exact arithmetic), PluDecoder (scale barcodes), PinHasher (Argon2id), UiResult.
android {
    namespace = "com.minimart.pos.core"
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
    testImplementation("junit:junit:4.13.2")
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation("com.lambdapioneer.argon2kt:argon2kt:1.4.0")
}
