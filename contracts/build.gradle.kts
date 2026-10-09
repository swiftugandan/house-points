plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
    explicitApi()
}

dependencies {
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
