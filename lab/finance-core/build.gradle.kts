import java.security.MessageDigest
import java.util.Properties

plugins { kotlin("jvm") }
apply(from = "../pure-jvm-boundaries.gradle.kts")
kotlin { jvmToolchain(17) }
dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// M8.4 offline oracle: original production sources compile in TEST only, never ship with Beta.
val oracleManifest = layout.projectDirectory.file("src/test/resources/parity/oracle-sha256.properties")
val oracleHashes = Properties().apply { oracleManifest.asFile.inputStream().use { load(it) } }
val oraclePaths = oracleHashes.stringPropertyNames().sorted()
val oracleSources = layout.buildDirectory.dir("generated/m8-oracle")
val prepareFinancialOracle by tasks.registering {
    inputs.file(oracleManifest)
    inputs.files(oraclePaths.map { rootProject.file(it) })
    outputs.dir(oracleSources)
    doLast {
        val sources = oraclePaths.associateWith { path ->
            check(path.startsWith("app/src/") && path.endsWith(".kt") && !path.contains(".."))
            val source = rootProject.file(path).readText(Charsets.UTF_8).replace("\r\n", "\n")
            val hash = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            check(hash == oracleHashes.getProperty(path)) { "M8 oracle provenance changed: $path; independent review required" }
            source
        }
        val output = oracleSources.get().asFile
        output.deleteRecursively()
        // Existing production test files pin fixture provenance only; CI already runs those tests.
        // Compile only original calculator/model code and our cross-run test, never duplicate old tests.
        sources.filterKeys { it.startsWith("app/src/main/kotlin/") }.forEach { (path, source) ->
            output.resolve(path).apply { parentFile.mkdirs(); writeText(source, Charsets.UTF_8) }
        }
    }
}
kotlin.sourceSets.getByName("test").kotlin.srcDir(oracleSources)
tasks.named("compileTestKotlin") { dependsOn(prepareFinancialOracle) }
val financialParityReport = layout.buildDirectory.file("reports/m8-parity/summary.txt")
tasks.test {
    outputs.file(financialParityReport)
    systemProperty("m8.parity.report", financialParityReport.get().asFile.absolutePath)
    // Cache hits restore this declared report; real execution cannot retain a stale earlier report.
    doFirst { financialParityReport.get().asFile.delete() }
}
