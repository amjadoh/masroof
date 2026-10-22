plugins { kotlin("jvm") }
apply(from = "../build-logic/pure-jvm-boundaries.gradle.kts")
kotlin { jvmToolchain(17) }
dependencies { testImplementation(libs.junit) }
