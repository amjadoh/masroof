plugins { kotlin("jvm") }
apply(from = "../build-logic/pure-jvm-boundaries.gradle.kts")
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":finance-core"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
}

// Offline experiment only: separate JVMs for teaching and reloading persisted pattern definitions.
tasks.register<JavaExec>("patternLifecycle") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.baraa.masroof.engine.bank.experiment.PatternLifecycleCli")
}

// Boundary tests inspect files outside this module. Include those files in the test cache key.
tasks.test {
    inputs.files(rootProject.fileTree(".") {
        include("*/build.gradle.kts", "bank-engine/src/main/kotlin/**/*.kt",
            "finance-core/src/main/kotlin/**/*.kt", "ml-assist/src/main/kotlin/**/*.kt", "app/src/main/kotlin/**/*.kt")
    }, rootProject.file("app/build.gradle.kts")).withPathSensitivity(PathSensitivity.RELATIVE)
}
