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

    // Signierung nur, wenn die Umgebungsvariablen gesetzt sind (GitHub-Release-Workflow).
    // Ohne sie bleibt das Release-APK unsigniert, wie F-Droid es erwartet.
    val keystoreFile = System.getenv("VUMETER_KEYSTORE")
    if (keystoreFile != null) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("VUMETER_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("VUMETER_KEY_ALIAS")
                keyPassword = System.getenv("VUMETER_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (keystoreFile != null) signingConfig = signingConfigs.getByName("release")
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
