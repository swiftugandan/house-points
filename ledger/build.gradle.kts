plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
    explicitApi()
}

dependencies {
    api(project(":contracts"))

    testImplementation(libs.junit)
}

tasks.test {
    // The sizing benchmark (NFR-PERF-1) runs with the rest; give it room.
    maxHeapSize = "2g"
}
