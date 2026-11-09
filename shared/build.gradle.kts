plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("com.google.devtools.ksp")
}

val kuiklyVersion = "2.4.2-2.0.21"

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.tencent.kuikly-open:core:$kuiklyVersion")
                implementation("com.tencent.kuikly-open:core-annotations:$kuiklyVersion")
            }
        }
        val androidMain by getting
    }
}

dependencies {
    // KSP generates KuiklyCoreEntry from @Page annotations.
    add("kspAndroid", "com.tencent.kuikly-open:core-ksp:$kuiklyVersion")
}

android {
    namespace = "com.example.kuiklynotes.shared"
    compileSdk = 34
    defaultConfig {
        minSdk = 21
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
