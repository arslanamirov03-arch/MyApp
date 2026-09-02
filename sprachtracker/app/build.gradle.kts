plugins {
    id("com.android.application")
}

// Optional release signing. Provide -PSPZ_STORE_FILE=... (plus password/alias properties)
// or the matching environment variables. Without them the release build falls back to the
// standard debug key so that `assembleRelease` still produces an installable APK.
fun prop(name: String): String? =
    (project.findProperty(name) as String?) ?: System.getenv(name)

val releaseStoreFile = prop("SPZ_STORE_FILE")

android {
    namespace = "de.sprechzeit.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.sprechzeit.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = prop("SPZ_STORE_PASSWORD")
                keyAlias = prop("SPZ_KEY_ALIAS")
                keyPassword = prop("SPZ_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        // woff2 is already compressed; storing it uncompressed keeps WebView font loading fast.
        noCompress += listOf("woff2")
    }
}
