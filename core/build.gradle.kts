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
    testImplementation("junit:junit:4.13.2")
}
