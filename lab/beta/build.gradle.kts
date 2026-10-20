plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.baraa.masroof.lab.beta"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        buildConfigField("String", "VERSION_NAME", "\"${providers.gradleProperty("appVersionName").orElse("0.3.18").get()}\"")
        buildConfigField("int", "VERSION_CODE", providers.gradleProperty("appVersionCode").orElse("71").get())
        buildConfigField("String", "APPLICATION_ID", "\"com.baraa.masroof\"")
        buildConfigField("String", "GITHUB_OWNER", "\"bakhshb\"")
        buildConfigField("String", "GITHUB_REPO", "\"Masroof\"")
        buildConfigField("boolean", "ENABLE_DESIGN_CATALOG", "false")
        buildConfigField("boolean", "ENABLE_MASROOF_BETA", "false")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["notPackage"] = "com.baraa.masroof.lab.beta.processdeath"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    testOptions {
        // Exercise the host app's runtime-permission behavior, not the library minSdk default.
        targetSdk = 34
        unitTests.isIncludeAndroidResources = true
    }
    sourceSets {
        getByName("main").assets.srcDir("src/replica/assets")
        getByName("main").java.setSrcDirs(listOf("src/replica/kotlin", "build/generated/productionReplica/kotlin"))
        getByName("main").res.setSrcDirs(listOf("src/replica/res", "build/generated/productionReplica/res"))
        getByName("test").java.setSrcDirs(listOf("src/replicaTest/kotlin", "build/generated/productionReplica/referenceKotlin"))
        getByName("androidTest").assets.srcDir("build/generated/productionReplica/fixtures")
        getByName("androidTest").manifest.srcFile("src/replicaAndroidTest/AndroidManifest.xml")
        getByName("androidTest").java.setSrcDirs(listOf("src/replicaAndroidTest/kotlin", "build/generated/productionReplica/referenceKotlin"))
    }
}

ksp {
    arg("room.schemaLocation", "$buildDir/replica-schemas")
}

dependencies {
    implementation(project(":ui-kit"))
    implementation(project(":lab:finance-core"))
    implementation(project(":lab:bank-engine"))
    implementation(libs.okhttp)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.test.manifest)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.serialization.json)
}

// Corpus checks read frozen source fixtures without executing Production parsers.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    inputs.files(rootProject.fileTree("app/src/test") {
        include("kotlin/com/baraa/masroof/bank/aljazira/AlJaziraParserRegressionTest.kt",
            "kotlin/com/baraa/masroof/bank/aljazira/CreditCardMessageHeuristicsTest.kt",
            "kotlin/com/baraa/masroof/bank/aljazira/ParsedEventDetailsFactsCharacterizationTest.kt",
            "resources/testdata/bank_aljazira/**/*.json")
    }).withPathSensitivity(PathSensitivity.RELATIVE)
}

val generateProductionReplica by tasks.registering(Exec::class) {
    inputs.dir(rootProject.file("app/src/main"))
    inputs.dir(rootProject.file("app/src/test/resources/testdata/bank_aljazira"))
    inputs.file("PRODUCTION_REPLICA_SOURCES.json")
    inputs.dir("replica-overlays")
    inputs.dir("testdata/reported")
    inputs.file("tools/generate_production_replica.py")
    outputs.dir(layout.buildDirectory.dir("generated/productionReplica"))
    commandLine("python3", "$projectDir/tools/generate_production_replica.py")
}
tasks.named("preBuild").configure { dependsOn(generateProductionReplica) }

kotlin.sourceSets {
    named("main") { kotlin.setSrcDirs(listOf("src/replica/kotlin", "build/generated/productionReplica/kotlin")) }
    named("test") { kotlin.setSrcDirs(listOf("src/replicaTest/kotlin", "build/generated/productionReplica/referenceKotlin")) }
    named("androidTest") { kotlin.setSrcDirs(listOf("src/replicaAndroidTest/kotlin", "build/generated/productionReplica/referenceKotlin")) }
}
