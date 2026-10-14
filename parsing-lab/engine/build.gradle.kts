plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

// Explicit, offline experiment. Never runs as part of app builds or changes lab rules.
tasks.register<JavaExec>("exportContextBenchmark") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.baraa.masroof.lab.engine.ContextBenchmarkExport")
    doFirst {
        args = listOf(
            providers.environmentVariable("LAB_BENCHMARK_INPUT").get(),
            providers.environmentVariable("LAB_BENCHMARK_OUTPUT").get(),
        )
    }
}

tasks.register<JavaExec>("exportEvidenceBenchmark") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.baraa.masroof.lab.engine.EvidenceBenchmarkExport")
    doFirst {
        args = listOf(providers.environmentVariable("LAB_BENCHMARK_INPUT").get(),
            providers.environmentVariable("LAB_BENCHMARK_OUTPUT").get())
    }
}
