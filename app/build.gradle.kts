plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.psiqos.spheres"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.psiqos.spheres"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0-beta.1"
    }

    signingConfigs {
        // A fixed key checked into the repo so every build (local or CI) is signed
        // identically and new versions install over old ones without uninstalling.
        create("shared") {
            storeFile = file("spheres.keystore")
            storePassword = "spheres"
            keyAlias = "spheres"
            keyPassword = "spheres"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
