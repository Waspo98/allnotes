import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

val requestedArch = (project.findProperty("targetArch")?.toString()
    ?: System.getProperty("os.arch")
    ?: "amd64").lowercase()

val isWindowsArm64 = requestedArch.contains("aarch64") || requestedArch.contains("arm64")

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":composeApp"))
    if (isWindowsArm64) {
        implementation(compose.desktop.windows_arm64)
    } else {
        implementation(compose.desktop.windows_x64)
    }
    implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "app.overbay.allnotes.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "AllNotes"
            packageVersion = "1.0.0"
            description = "AllNotes — Material 3 Expressive OneNote/Word Workspace"
            vendor = "Overbay.app"

            windows {
                menu = true
                menuGroup = "Overbay.app"
                shortcut = true
                upgradeUuid = "8B27D174-5C9A-4D2B-A2E1-9E45A092C811"
                iconFile.set(project.file("src/main/resources/allnotes.ico"))
            }
        }
    }
}
