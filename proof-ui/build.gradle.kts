plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "moe.frost.kochenite.proof.ui"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
dependencies {
    implementation(project(":proof-storage"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation:1.11.2")
    implementation("androidx.compose.material3:material3:1.5.0-alpha17")
}
