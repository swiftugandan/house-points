plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.housepoints.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.housepoints"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    // Release signing comes from Gradle properties kept outside the repository (e.g. ~/.gradle/gradle.properties):
    // housepoints.signing.storeFile, .storePassword, .keyAlias, .keyPassword. Without them release stays unsigned.
    val signingStore = providers.gradleProperty("housepoints.signing.storeFile").orNull
    signingConfigs {
        if (signingStore != null) {
            create("release") {
                storeFile = file(signingStore)
                storePassword = providers.gradleProperty("housepoints.signing.storePassword").get()
                keyAlias = providers.gradleProperty("housepoints.signing.keyAlias").get()
                keyPassword = providers.gradleProperty("housepoints.signing.keyPassword").get()
            }
        }
    }

    buildTypes {
        // Debug builds install alongside the release, so testing never wipes a family's real data.
        debug {
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "House Points (debug)")
        }
        release {
            resValue("string", "app_name", "House Points")
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}

dependencies {
    implementation(project(":ledger"))
    implementation(project(":sync"))
    implementation(project(":data"))
    implementation(project(":nearby"))
    implementation(project(":lan"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.code.scanner)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
