plugins {
    id("com.android.application") version "8.13.2"
    id("org.jetbrains.kotlin.android") version "2.2.20"
}

android {
    namespace = "br.com.devoaceitar"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.devoaceitar"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        manifestPlaceholders["applicationLabel"] = "Devo aceitar?"
    }

    buildTypes {
        create("simulator") {
            applicationIdSuffix = ".simulador"
            manifestPlaceholders["applicationLabel"] = "Devo aceitar? Simulador"
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}
