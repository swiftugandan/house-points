plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.housepoints.nearby"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    // Library test APKs otherwise target minSdk, which Play Protect blocks as "built for an older Android".
    testOptions {
        targetSdk = libs.versions.targetSdk.get().toInt()
    }
    lint {
        targetSdk = libs.versions.targetSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
    explicitApi()
}

dependencies {
    api(project(":sync"))
    implementation(libs.play.services.nearby)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(testFixtures(project(":sync")))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
