plugins {
    id("com.android.application")
    kotlin("android")
}

val kuiklyVersion = "2.4.2-2.0.21"

android {
    namespace = "com.example.kuiklynotes"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.kuiklynotes"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    signingConfigs {
        val ks = System.getenv("NOTES_KEYSTORE")
        if (!ks.isNullOrEmpty() && file(ks).exists()) {
            create("ciRelease") {
                storeFile = file(ks)
                storePassword = System.getenv("NOTES_STORE_PASS") ?: "notes123456"
                keyAlias = System.getenv("NOTES_KEY_ALIAS") ?: "notes"
                keyPassword = System.getenv("NOTES_KEY_PASS") ?: "notes123456"
            }
        }
    }

    buildTypes {
        release {
            // Use a real keystore when CI provides one; otherwise fall back to the
            // debug certificate so the release APK is always installable.
            signingConfig = signingConfigs.findByName("ciRelease") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation("com.tencent.kuikly-open:core:$kuiklyVersion")
    implementation("com.tencent.kuikly-open:core-render-android:$kuiklyVersion")
}
