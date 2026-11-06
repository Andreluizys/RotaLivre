import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.com.andreluizs.rotalivre"
    compileSdk = 34
    defaultConfig {
        applicationId = "br.com.andreluizs.rotalivre"
        minSdk = 26
        targetSdk = 34
        versionCode = 7
        versionName = "1.6"
    }
    // Assinatura de release: lê as credenciais de ~/.rotalivre/keystore.properties (fora do repositório).
    val ksFile = File(System.getProperty("user.home"), ".rotalivre/keystore.properties")
    val ks = Properties().apply { if (ksFile.exists()) ksFile.inputStream().use { load(it) } }
    signingConfigs {
        if (ks.isNotEmpty()) create("release") {
            storeFile = file(ks.getProperty("storeFile"))
            storePassword = ks.getProperty("storePassword")
            keyAlias = ks.getProperty("keyAlias")
            keyPassword = ks.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (ks.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    testImplementation("junit:junit:4.13.2")
}
