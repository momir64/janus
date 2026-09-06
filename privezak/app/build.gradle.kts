plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val keystore = providers.gradleProperty("PRIVEZAK_STORE_FILE").orNull

android {
    namespace = "rs.moma.janus.privezak"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "rs.moma.janus.privezak"
        // CredentialProviderService needs minSdk 34; mitigating StrandHogg attack needs 30:
        // https://developer.android.com/privacy-and-security/risks/strandhogg
        minSdk = 34
        targetSdk = 37
        versionCode = 6
        versionName = "0.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystore != null) create("release") {
            storeFile = file(keystore)
            storePassword = providers.gradleProperty("PRIVEZAK_STORE_PASSWORD").get()
            keyPassword = providers.gradleProperty("PRIVEZAK_KEY_PASSWORD").get()
            keyAlias = providers.gradleProperty("PRIVEZAK_KEY_ALIAS").get()
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = true
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.core.ktx)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}