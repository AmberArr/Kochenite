import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val releaseSigningFile = rootProject.file(".local/signing/keystore.properties")
val releaseSigning =
    Properties().apply {
        if (releaseSigningFile.isFile) releaseSigningFile.inputStream().use { load(it) }
    }
// The Sentry DSN only permits event submission; it is not a credential.
val sentryDsn = providers.gradleProperty("kocheniteSentryDsn").orElse("").get()

android {
    namespace = "moe.frost.kochenite"
    compileSdk = 36
    buildToolsVersion = "37.0.0"
    defaultConfig {
        applicationId = "moe.frost.kochenite"
        minSdk = 26
        targetSdk = 36
        versionCode =
            providers
                .gradleProperty("releaseVersionCode")
                .orElse("2")
                .get()
                .toInt()
        versionName = "0.1.$versionCode"
        testApplicationId = "moe.frost.kochenite.debug.test"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SENTRY_DSN", "\"\"")
    }
    signingConfigs {
        if (releaseSigningFile.isFile) {
            create("release") {
                storeFile = file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            // Debug builds run synthetic device tests; report them only when explicitly requested.
            if (providers.gradleProperty("kocheniteSentryDebug").orNull == "true") {
                buildConfigField("String", "SENTRY_DSN", "\"$sentryDsn\"")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("String", "SENTRY_DSN", "\"$sentryDsn\"")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
dependencies {
    implementation(project(":domain"))
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation:1.11.2")
    implementation("androidx.compose.material3:material3:1.5.0-alpha17")
    implementation(project(":persistence"))
    // Core only: no native crash handler, session replay or automatic Gradle instrumentation.
    implementation("io.sentry:sentry-android-core:8.58.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.11.2")
    androidTestImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.11.2")
}
val requireReleaseSigning by tasks.registering {
    doLast {
        check(
            releaseSigningFile.isFile,
        ) { "Release signing is missing. Restore .local/signing; never regenerate an existing release identity." }
        check(file(releaseSigning.getProperty("storeFile")).isFile) { "Release keystore is missing; restore it from backup." }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(requireReleaseSigning) }
