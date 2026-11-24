import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Verziószám a git commitok számából: minden push után nő, így az app észreveszi a frissítést.
// (A CI ugyanezzel a képlettel írja a version.json-t — ha itt változtatsz, ott is: .github/workflows/build.yml)
val gitCommits: Int = try {
    providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
        .standardOutput.asText.get().trim().toInt()
} catch (e: Exception) { 0 }

// Saját aláíró kulcs: helyben a keystore.properties-ből (NEM kerül a gitbe), a CI-ban környezeti
// változókból (GitHub Secrets). Ha egyik sincs, marad a debug kulcs.
val ks = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun ksValue(key: String, env: String): String? = ks.getProperty(key) ?: System.getenv(env)?.takeIf { it.isNotBlank() }
val ksFile = ksValue("storeFile", "LX_KEYSTORE_FILE")

android {
    namespace = "hu.lextronic.radio"
    compileSdk = 37      // a Compose 1.12 és az újabb androidx könyvtárak ezt kérik

    defaultConfig {
        applicationId = "hu.lextronic.radio"
        minSdk = 26
        targetSdk = 36       // Android 16 (a Play-en is ez a minimum új appoknál)
        versionCode = 100 + gitCommits
        versionName = "1.6.$gitCommits"
    }
    if (ksFile != null) signingConfigs {
        create("lx") {
            storeFile = file(ksFile)
            storePassword = ksValue("storePassword", "LX_KEYSTORE_PASSWORD")
            keyAlias = ksValue("keyAlias", "LX_KEY_ALIAS")
            keyPassword = ksValue("keyPassword", "LX_KEY_PASSWORD")
        }
    }
    buildTypes {
        // a debug (▶ Run) is a saját kulccsal: így a telefonon lévő app a GitHubról is frissíthető
        debug {
            if (ksFile != null) signingConfig = signingConfigs.getByName("lx")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (ksFile != null) "lx" else "debug")
        }
    }
    lint { checkReleaseBuilds = false }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    // az ikonkészlet 1.7.8-nál befagyott, már nem a BOM kezeli
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.1")
    implementation("androidx.core:core-ktx:1.19.1")

    val media3 = "1.11.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-session:$media3")

    implementation("io.coil-kt:coil-compose:2.7.0")

    // kezdőképernyő-widgetek
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
}
