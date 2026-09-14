plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.quieta.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.shizuku.api)
    // JVM unit tests need org.json (Android provides it at runtime).
    testImplementation("org.json:json:20240303")
    testImplementation("junit:junit:4.13.2")
}
