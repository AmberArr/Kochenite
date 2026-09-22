plugins {
    base
    id("com.diffplug.spotless") version "8.10.2"
    id("com.android.application") version "9.3.1" apply false
    id("com.android.library") version "9.3.1" apply false
    id("org.jetbrains.kotlin.jvm") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10" apply false
    id("com.google.devtools.ksp") version "2.3.11" apply false
}

spotless {
    kotlin {
        target("app/src/**/*.kt", "domain/src/**/*.kt", "persistence/src/**/*.kt", "recognition-contract/src/**/*.kt")
        ktlint("1.8.0").setEditorConfigPath(rootProject.file(".editorconfig"))
    }
    kotlinGradle {
        target(
            "*.gradle.kts",
            "app/build.gradle.kts",
            "domain/build.gradle.kts",
            "persistence/build.gradle.kts",
            "recognition-contract/build.gradle.kts",
        )
        ktlint("1.8.0").setEditorConfigPath(rootProject.file(".editorconfig"))
    }
}

val testTooling by tasks.registering(Exec::class) {
    group = "verification"
    description = "Runs host tests for the repository scripts."
    commandLine(listOf("node", "--test") + fileTree("scripts") { include("*.test.mjs") }.files.sorted().map { it.path })
}

tasks.register("verify") {
    group = "verification"
    description = "Checks formatting, Android lint, JVM tests and repository scripts without a device or release key."
    dependsOn(
        "spotlessCheck",
        ":app:lintDebug",
        ":persistence:lintDebug",
        ":domain:test",
        ":app:testDebugUnitTest",
        ":recognition-contract:check",
        testTooling,
    )
}

tasks.named("check") { dependsOn("verify") }
