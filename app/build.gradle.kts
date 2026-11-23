import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.ap"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.ap"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"

        // 只保留常见手机 CPU 架构，控制 APK 体积
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // 签名密码从本地 keystore.properties 读，这个文件不进仓库（.gitignore 已排除）
    // 密钥文件和密码文件都在，才启用 release 签名；都没有就只编 debug
    val keystoreFile = rootProject.file("keystore/perch-release.keystore")
    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties()
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { keystoreProps.load(it) }
    }
    val hasReleaseSigning = keystoreFile.exists() && keystorePropsFile.exists()
    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = keystoreFile
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("io.coil-kt:coil-compose:2.7.0")

    // 离线语音识别引擎（AAR 带全部 CPU 架构的原生库）
    implementation(files("libs/sherpa-onnx-2.0.3.aar"))
}
