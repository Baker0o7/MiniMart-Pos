plugins {
    alias(libs.plugins.kotlin.jvm)
}

// :core — pure Kotlin/JVM (no Android): Money (cent-exact arithmetic), PluDecoder (scale barcodes),
// BackupCrypto (AES-GCM), MpesaSmsParser, UiResult. Because it has no Android dependency its tests run
// on a plain JVM in seconds, and nothing Android-specific can creep back in without breaking the build.
// (PIN hashing needs the native Argon2 library, so PinHasher lives in :data.)
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    // @Qualifier / @Inject annotations (the Hilt-generated code lives in the Android modules).
    api("javax.inject:javax.inject:1")
    testImplementation("junit:junit:4.13.2")
}
