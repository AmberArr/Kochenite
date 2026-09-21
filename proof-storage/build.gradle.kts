plugins {
    id("com.android.library")
    id("com.google.devtools.ksp")
}
android {
    namespace = "moe.frost.kochenite.proof.storage"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
ksp { arg("room.schemaLocation", file("schemas").absolutePath) }
dependencies {
    api("androidx.room:room-runtime:2.8.4")
    implementation("net.zetetic:sqlcipher-android:4.17.0")
    implementation("androidx.sqlite:sqlite:2.6.2")
    ksp("androidx.room:room-compiler:2.8.4")
}
