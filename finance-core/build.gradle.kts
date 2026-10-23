plugins {
    kotlin("jvm")
    alias(libs.plugins.detekt)
}
apply(from = "../build-logic/pure-jvm-boundaries.gradle.kts")
kotlin { jvmToolchain(17) }
dependencies {
    testImplementation(libs.junit)
    detektPlugins(libs.detekt.formatting)
}
detekt {
    config.setFrom("$rootDir/detekt.yml")
    buildUponDefaultConfig = false
    parallel = true
}
