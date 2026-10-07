plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "net.schmalenstroer.vumeter"
    compileSdk = 35

    defaultConfig {
        applicationId = "net.schmalenstroer.vumeter"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // F-Droid: keine proprietären Abhängigkeits-Metadaten ins APK schreiben
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
