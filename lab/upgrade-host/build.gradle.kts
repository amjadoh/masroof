import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// CI-only synthetic signed update rehearsal; users enter Lab from the existing app.
android {
    namespace = "com.baraa.masroof.lab.upgrade"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.baraa.masroof.beta.upgrade.rehearsal"
        minSdk = 26
        targetSdk = 34
        testApplicationId = "com.baraa.masroof.beta.upgrade.rehearsal.test"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("rehearsal") {
            storeFile = layout.buildDirectory.file("rehearsal-signing/test-only.jks").get().asFile
            storePassword = "synthetic-test-only"
            keyAlias = "beta-upgrade-test"
            keyPassword = "synthetic-test-only"
        }
    }
    // Both versions share an ephemeral dedicated test certificate.
    buildTypes.getByName("debug").signingConfig = null
    flavorDimensions += "rehearsal"
    productFlavors {
        create("before") { dimension = "rehearsal"; versionCode = 1; versionName = "rehearsal-1"; signingConfig=signingConfigs.getByName("rehearsal") }
        create("after") { dimension = "rehearsal"; versionCode = 2; versionName = "rehearsal-2"; signingConfig=signingConfigs.getByName("rehearsal") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
    sourceSets.getByName("androidTest").java.srcDir("../beta/replica-verification/kotlin")
}
androidComponents.beforeVariants(androidComponents.selector().withBuildType("release")) {it.enable=false}

// An ephemeral, dedicated test certificate; never the app's release or default debug key.
val rehearsalKey = layout.buildDirectory.file("rehearsal-signing/test-only.jks")
val generateRehearsalKey by tasks.registering(Exec::class) {
    outputs.file(rehearsalKey)
    onlyIf { !rehearsalKey.get().asFile.exists() }
    doFirst {
        rehearsalKey.get().asFile.parentFile.mkdirs()
        val suffix = if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""
        val keytool = File(System.getProperty("java.home"), "bin/keytool$suffix").absolutePath
        commandLine(keytool, "-genkeypair", "-keystore", rehearsalKey.get().asFile.absolutePath,
            "-storepass", "synthetic-test-only", "-keypass", "synthetic-test-only", "-alias", "beta-upgrade-test",
            "-keyalg", "RSA", "-keysize", "2048", "-validity", "2", "-dname", "CN=Beta Upgrade Test Only")
    }
}
tasks.matching { it.name.startsWith("validateSigning") }.configureEach { dependsOn(generateRehearsalKey) }

dependencies {
    implementation(project(":lab:beta"))
    // Make the explicitly removed debug-only ComponentActivity visible to manifest lint.
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.work.runtime.ktx)
    androidTestImplementation(project(":lab:finance-core"))
    androidTestImplementation(libs.kotlinx.coroutines.android)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
