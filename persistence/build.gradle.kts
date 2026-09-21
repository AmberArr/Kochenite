plugins {
    id("com.android.library")
    id("com.google.devtools.ksp")
}
android {
    namespace = "moe.frost.kochenite.persistence"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        testApplicationId = "moe.frost.kochenite.debug.storage.test"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    sourceSets.named("androidTest") { assets.directories.add("schemas") }
}
ksp { arg("room.schemaLocation", file("schemas").absolutePath) }
dependencies {
    implementation(project(":domain"))
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.sqlite:sqlite:2.6.2")
    implementation("androidx.sqlite:sqlite-framework:2.6.2")
    ksp("androidx.room:room-compiler:2.8.4")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.room:room-testing:2.8.4")
    androidTestImplementation("junit:junit:4.13.2")
}
