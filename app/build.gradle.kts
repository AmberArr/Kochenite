import java.util.Properties

plugins { id("com.android.application") }

val proofSigningFile = rootProject.file(".local/proof-signing/keystore.properties")
val proofSigning = Properties().apply {
    if (proofSigningFile.isFile) proofSigningFile.inputStream().use { load(it) }
}
android {
    namespace = "moe.frost.kochenite"
    compileSdk = 36
    buildToolsVersion = "37.0.0"
    defaultConfig {
        // Temporary identity until the foundation uses its durable signing key.
        applicationId = "moe.frost.kochenite.debug"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "gradle-proof"
        testApplicationId = "moe.frost.kochenite.debug.test"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (proofSigningFile.isFile) create("proof") {
            storeFile = file(proofSigning.getProperty("storeFile"))
            storePassword = proofSigning.getProperty("storePassword")
            keyAlias = proofSigning.getProperty("keyAlias")
            keyPassword = proofSigning.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            applicationIdSuffix = ".releaseproof"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("proof")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
dependencies {
    implementation(project(":proof-ui"))
    androidTestImplementation(project(":proof-storage"))
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.11.2")
    androidTestImplementation("junit:junit:4.13.2")
}
