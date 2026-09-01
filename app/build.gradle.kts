import java.util.Properties
import java.io.FileInputStream
import java.security.KeyStore
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// Release signing config. signing/keystore.properties + signing/lexis-release.jks are
// committed to this private repo on purpose, so any build — CI or local — signs with the
// same key and updates install over the previous version without losing data.
// A root-level keystore.properties (gitignored) overrides it if present.
val keystorePropsFile = listOf(
    rootProject.file("keystore.properties"),
    rootProject.file("signing/keystore.properties"),
).firstOrNull { it.exists() }
val keystoreProps = Properties()
val hasReleaseSigning = keystorePropsFile != null
if (keystorePropsFile != null) {
    keystoreProps.load(FileInputStream(keystorePropsFile))
}

fun sha256Fingerprint(storeFile: File, storePassword: String, alias: String, keyPassword: String): String {
    return try {
        val ks = KeyStore.getInstance("JKS")
        FileInputStream(storeFile).use { ks.load(it, storePassword.toCharArray()) }
        val cert = ks.getCertificate(alias) ?: return "unavailable"
        val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        digest.joinToString(":") { String.format("%02X", it) }
    } catch (e: Exception) {
        "unavailable"
    }
}

android {
    namespace = "com.lexis.words"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lexis.words"
        minSdk = 26
        targetSdk = 34
        versionCode = 41
        versionName = "1.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val alias = keystoreProps.getProperty("keyAlias") ?: "lexis-upload"
        val storePassword = keystoreProps.getProperty("storePassword") ?: ""
        val keyPassword = keystoreProps.getProperty("keyPassword") ?: ""
        val fingerprint = if (hasReleaseSigning) {
            sha256Fingerprint(
                rootProject.file(keystoreProps.getProperty("storeFile")),
                storePassword, alias, keyPassword
            )
        } else "not signed with release key (local/debug build)"

        buildConfigField("String", "SIGNING_KEYSTORE_NAME", "\"lexis-release.jks\"")
        buildConfigField("String", "SIGNING_KEY_ALIAS", "\"$alias\"")
        buildConfigField("String", "SIGNING_STORE_PASSWORD", "\"${if (hasReleaseSigning) storePassword else "—"}\"")
        buildConfigField("String", "SIGNING_KEY_PASSWORD", "\"${if (hasReleaseSigning) keyPassword else "—"}\"")
        buildConfigField("String", "SIGNING_SHA256", "\"$fingerprint\"")
        buildConfigField("boolean", "HAS_REAL_SIGNING", "$hasReleaseSigning")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    implementation("io.coil-kt:coil-compose:2.6.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
