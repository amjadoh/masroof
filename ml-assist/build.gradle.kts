plugins { kotlin("jvm") }
apply(from = "../build-logic/pure-jvm-boundaries.gradle.kts")
kotlin { jvmToolchain(17) }
dependencies { testImplementation(libs.junit) }

tasks.register<JavaExec>("trainRecurringSeed") {
    group = "verification"
    description = "Fit/evaluate a reviewed numerical corpus with the on-device implementation"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.baraa.masroof.analytics.RecurringTrainingCliKt")
    args(providers.gradleProperty("recurringCorpus").getOrElse("$projectDir/tools/recurring-corpus.tsv"),
        providers.gradleProperty("recurringOutput").getOrElse("$projectDir/src/main/resources/ml/recurring-seed.json"),
        providers.gradleProperty("recurringReport").getOrElse("$projectDir/RECURRING_EVALUATION.json"))
    if (providers.gradleProperty("recurringCheck").orNull == "true") args("--check")
}
