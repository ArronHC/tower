import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val generatedTowerAssetsDir = layout.buildDirectory.dir("generated/towerAssets")

val syncTowerAssets by tasks.registering(Sync::class) {
    val repoRoot = rootProject.projectDir.parentFile
    into(generatedTowerAssetsDir)
    from(File(repoRoot, "Tower/Resources")) {
        into("tower_resources")
    }
    from(File(repoRoot, "docs/images/clients")) {
        into("tower_clients")
    }
    from(File(repoRoot, "docs/images/icon.png")) {
        rename { "tower_icon.png" }
    }
}

val localSigningProps = Properties().apply {
    val f = rootProject.file("signing.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingValue(envKey: String, propKey: String): String? =
    System.getenv(envKey)?.takeIf { it.isNotBlank() }
        ?: localSigningProps.getProperty(propKey)?.takeIf { it.isNotBlank() }

val releaseKeystorePath = signingValue("TOWER_KEYSTORE_PATH", "storeFile")
val releaseKeystorePassword = signingValue("TOWER_KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("TOWER_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("TOWER_KEY_PASSWORD", "keyPassword")
val hasReleaseSigning = releaseKeystorePath != null &&
    File(releaseKeystorePath).exists() &&
    releaseKeystorePassword != null &&
    releaseKeyAlias != null &&
    releaseKeyPassword != null

android {
    namespace = "com.jzb.tower.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jzb.tower"
        minSdk = 26
        targetSdk = 35
        versionCode = 84
        versionName = "1.0.34"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = File(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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

    sourceSets {
        getByName("main") {
            assets.srcDir(generatedTowerAssetsDir)
        }
    }

    androidResources {
        noCompress += listOf("bin")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

tasks.named("preBuild") {
    dependsOn(syncTowerAssets)
}

dependencies {
    implementation(project(":core"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
