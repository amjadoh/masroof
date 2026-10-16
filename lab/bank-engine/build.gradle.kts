plugins { kotlin("jvm") }
apply(from = "../pure-jvm-boundaries.gradle.kts")
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":lab:finance-core"))
    testImplementation(libs.junit)
}
