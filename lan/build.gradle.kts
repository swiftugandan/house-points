plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.housepoints.lan"
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

    testOptions {
        unitTests.all { test ->
            // Live desktop-peer test (LiveDesktopPeerTest): -Php.peer=host:port -Php.pairing=hp1:…
            listOf("hp.peer", "hp.pairing").forEach { name ->
                providers.gradleProperty(name).orNull?.let { test.systemProperty(name, it) }
            }
        }
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
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(testFixtures(project(":sync")))
}
