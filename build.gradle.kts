import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.11.0"
}

// Latest released Rider. Verified on every PR (see `pluginVerification` below).
val CURRENT_RIDER = "2026.2"

group = "com.github.iamr8"
// Single source of truth for the plugin version (also consumed by CI / releases).
// CI overrides it for EAP dev builds via -PpluginVersion=<date+build>.
version = providers.gradleProperty("pluginVersion").orNull?.takeIf { it.isNotBlank() }
    ?: file("VERSION").readText().trim()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Locally: build against an installed Rider (no big download, exact API match).
        // Elsewhere (CI, no local install): download the matching Rider SDK.
        if (file("/Applications/Rider.app").exists()) {
            local("/Applications/Rider.app")
        } else {
            rider("2026.1.4", useInstaller = false)
        }
        // libman.json is JSON — the intentions read/edit it via the bundled JSON PSI.
        bundledPlugin("com.intellij.modules.json")
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    // No searchable settings in this plugin.
    buildSearchableOptions = false

    pluginConfiguration {
        ideaVersion {
            // 243 = Rider 2024.3, the oldest release on JBR 21 (our Java-21 bytecode needs it).
            sinceBuild = "243"
            // No upper bound: stay compatible with future IDE builds (Marketplace-friendly).
            untilBuild = provider { null }
        }
    }

    // `publishPlugin` uses this token (JetBrains Marketplace); provided via env in CI.
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    // `verifyPlugin` (IntelliJ Plugin Verifier). Two scopes, because every extra IDE is a
    // multi-GB download:
    //   -PverifierIdes=current -> the current Rider only (the PR check).
    //   (default)              -> the whole supported range (compatibility.yml).
    pluginVerification {
        ides {
            if (providers.gradleProperty("verifierIdes").orNull == "current") {
                create(IntelliJPlatformType.Rider, CURRENT_RIDER) { useInstaller = false }
            } else {
                create(IntelliJPlatformType.Rider, "2024.3.6") { useInstaller = false }
                create(IntelliJPlatformType.Rider, "2025.2.4") { useInstaller = false }
                create(IntelliJPlatformType.Rider, "2026.1.4") { useInstaller = false }
                create(IntelliJPlatformType.Rider, CURRENT_RIDER) { useInstaller = false }
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        // Emit real Java default methods instead of Kotlin delegating overrides, so implementing
        // platform interfaces doesn't generate usages of their deprecated/experimental defaults.
        freeCompilerArgs.add("-jvm-default=no-compatibility")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.test {
    useJUnit()
}
