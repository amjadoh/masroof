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

// Explicit M6 candidate timing on immutable regression input, separate from unit-test timing.
tasks.register<JavaExec>("exportSharedNetworkBenchmark") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.baraa.masroof.lab.engine.SharedNetworkBenchmarkExport")
    doFirst {
        args = listOf(layout.projectDirectory.dir("src/test/resources/testdata/bank_aljazira").asFile.absolutePath,
            layout.buildDirectory.dir("reports/shared-network-benchmark").get().asFile.absolutePath)
    }
}

// Explicit fail-closed local measurement. Private input is never copied into build artifacts.
tasks.register<JavaExec>("assessM6Holdout") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.baraa.masroof.lab.engine.acceptance.M6AcceptanceExport")
    doFirst {
        args = listOf(layout.projectDirectory.asFile.absolutePath,
            layout.buildDirectory.dir("reports/m6-acceptance").get().asFile.absolutePath,
            providers.environmentVariable("M6_REVIEWED_HOLDOUT").orNull.orEmpty(),
            providers.environmentVariable("M6_ACCEPTANCE_POLICY").orNull.orEmpty())
    }
}
