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
    // Timing-based checks are machine-dependent; they live in the benchmark task below.
    exclude("**/SizingBenchmark*")
}

val benchmark by tasks.registering(Test::class) {
    description = "NFR-PERF-1 sizing benchmark (machine-dependent timing)."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/SizingBenchmark*")
    maxHeapSize = "2g"
    testLogging { showStandardStreams = true }
}
