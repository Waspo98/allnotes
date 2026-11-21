import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val localProperties = Properties().apply {
    val localPropFile = rootProject.file("local.properties")
    if (localPropFile.exists()) {
        localPropFile.inputStream().use { load(it) }
    }
}
val releaseKeystorePath: String? = System.getenv("KEYSTORE_PATH") ?: localProperties.getProperty("release.keystore.path")
val releaseKeystorePassword: String? = System.getenv("KEYSTORE_PASSWORD") ?: localProperties.getProperty("release.keystore.password")
val releaseKeyAlias: String? = System.getenv("KEY_ALIAS") ?: localProperties.getProperty("release.key.alias")
val releaseKeyPassword: String? = System.getenv("KEY_PASSWORD") ?: localProperties.getProperty("release.key.password")
val resolvedKeystoreFile = releaseKeystorePath?.let { path ->
    val f = File(path)
    if (f.isAbsolute) f else rootProject.file(path)
}
val hasReleaseSigning = resolvedKeystoreFile != null &&
    resolvedKeystoreFile.exists() &&
    !releaseKeystorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

if (gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) } && !hasReleaseSigning) {
    throw GradleException(
        "Release signing keystore is missing or incomplete. Set KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, and KEY_PASSWORD (or release.keystore.* in local.properties)."
    )
}

fun isBetaBranch(): Boolean {
    project.findProperty("isBeta")?.toString()?.trim()?.lowercase()?.toBooleanStrictOrNull()?.let { return it }
    System.getenv("ALLNOTES_IS_BETA")?.trim()?.lowercase()?.toBooleanStrictOrNull()?.let { return it }
    return try {
        val headFile = rootProject.file(".git/HEAD")
        if (headFile.exists()) {
            val content = headFile.readText().trim()
            content.endsWith("/beta") || content.equals("beta", ignoreCase = true)
        } else {
            false
        }
    } catch (_: Exception) {
        false
    }
}

val isBetaApp = isBetaBranch()

android {
    namespace = "app.overbay.allnotes.android"
    compileSdk = 37

    defaultConfig {
        applicationId = if (isBetaApp) "app.overbay.allnotes.beta" else "app.overbay.allnotes"
        resValue("string", "app_name", if (isBetaApp) "AllNotes Beta" else "AllNotes")
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = if (isBetaApp) "0.1.1-beta" else "0.1.1"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = resolvedKeystoreFile
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            ndk {
                abiFilters += listOf("arm64-v8a", "x86_64")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        resValues = true
    }

    sourceSets {
        getByName("main") {
            res.directories.add(if (isBetaApp) "src/beta/res" else "src/prod/res")
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":composeApp"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel)
}

fun MinimalExternalModuleDependency.asNotation(): String = "${module}:${versionConstraint.requiredVersion}"

configurations.all {
    resolutionStrategy {
        force(
            libs.androidx.core.asProvider().get().asNotation(),
            libs.androidx.core.ktx.get().asNotation(),
            libs.androidx.lifecycle.viewmodel.compose.get().asNotation(),
            libs.androidx.lifecycle.runtime.compose.get().asNotation(),
            libs.androidx.lifecycle.viewmodel.asProvider().get().asNotation(),
            libs.androidx.lifecycle.runtime.asProvider().get().asNotation(),
            libs.androidx.lifecycle.runtime.ktx.get().asNotation(),
            libs.androidx.lifecycle.viewmodel.ktx.get().asNotation(),
            libs.androidx.lifecycle.common.get().asNotation()
        )
    }
}

val targetApk = layout.buildDirectory.file("outputs/apk/debug/androidApp-debug.apk")
val targetReleaseApk = layout.buildDirectory.file("outputs/apk/release/androidApp-release.apk")

tasks.register<CopyDebugApkTask>("copyDebugApkToRoot") {
    inputFile.set(targetApk)
    betaBuild.set(isBetaApp)
    rootDirectory.set(layout.projectDirectory.dir(".."))
}

tasks.register<CopyDebugApkTask>("copyReleaseApkToRoot") {
    inputFile.set(targetReleaseApk)
    betaBuild.set(isBetaApp)
    rootDirectory.set(layout.projectDirectory.dir(".."))
    mustRunAfter("assembleRelease")
}

val targetBundle = layout.buildDirectory.file("outputs/bundle/release/androidApp-release.aab")
val destinationBundle = File(rootDir, "AllNotes-release.aab")

tasks.register<CopyArtifactTask>("copyReleaseBundleToRoot") {
    inputFile.set(targetBundle)
    outputFile.set(destinationBundle)
}

abstract class CopyDebugApkTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputFile: RegularFileProperty

    @get:Input
    abstract val betaBuild: Property<Boolean>

    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    @TaskAction
    fun copy() {
        val src = inputFile.get().asFile
        if (src.exists()) {
            val root = rootDirectory.get().asFile
            val standardApk = File(root, "AllNotes-debug.apk")
            val betaApk = File(root, "AllNotesBeta-debug.apk")
            if (betaBuild.get()) {
                src.copyTo(betaApk, overwrite = true)
                if (standardApk.exists()) standardApk.delete()
            } else {
                src.copyTo(standardApk, overwrite = true)
                if (betaApk.exists()) betaApk.delete()
            }
        }
    }
}

abstract class CopyArtifactTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun copy() {
        val src = inputFile.get().asFile
        if (src.exists()) {
            src.copyTo(outputFile.get().asFile, overwrite = true)
        }
    }
}

afterEvaluate {
    tasks.findByName("assembleDebug")?.finalizedBy("copyDebugApkToRoot")
    tasks.findByName("bundleRelease")?.finalizedBy("copyReleaseBundleToRoot")
}
