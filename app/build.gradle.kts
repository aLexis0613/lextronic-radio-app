import java.util.Properties

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
    compileSdk = 35

    defaultConfig {
        applicationId = "hu.lextronic.radio"
        minSdk = 26
        targetSdk = 35
        versionCode = 100 + gitCommits
        versionName = "1.6.$gitCommits"
    }
    if (ksFile != null) signingConfigs {
        create("lx") {
            storeFile = file(ksFile!!)
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
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")

    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-session:$media3")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
