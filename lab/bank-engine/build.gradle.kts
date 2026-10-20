plugins { kotlin("jvm") }
apply(from = "../pure-jvm-boundaries.gradle.kts")
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":lab:finance-core"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
}

// Boundary tests inspect files outside this module. Include those files in the test cache key.
tasks.test {
    inputs.files(rootProject.fileTree("lab") {
        include("*/build.gradle.kts", "bank-engine/src/main/kotlin/**/*.kt",
            "finance-core/src/main/kotlin/**/*.kt", "beta/src/main/kotlin/**/*.kt", "beta/src/replica/kotlin/**/*.kt")
    }, rootProject.file("app/build.gradle.kts")).withPathSensitivity(PathSensitivity.RELATIVE)
}

tasks.test {
    inputs.dir(rootProject.file("app/src/test/resources/testdata/bank_aljazira"))
    inputs.dir(rootProject.file("lab/beta/testdata/reported"))
    inputs.files(rootProject.fileTree("lab/beta/src/replica/assets/ml") { include("*.json") })
    outputs.dir(layout.buildDirectory.dir("model-training"))
}
