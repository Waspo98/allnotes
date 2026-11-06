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

fun isBetaBranch(): Boolean {
    if (project.findProperty("isBeta")?.toString()?.toBoolean() == true) return true
    if (System.getenv("ALLNOTES_IS_BETA")?.toBoolean() == true) return true
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
        versionCode = 1
        versionName = if (isBetaApp) "0.10-beta" else "0.10"
    }

    signingConfigs {
        create("release") {
            if (resolvedKeystoreFile != null && resolvedKeystoreFile.exists()) {
                storeFile = resolvedKeystoreFile
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            } else {
                initWith(getByName("debug"))
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
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":composeApp"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel)
}

configurations.all {
    resolutionStrategy {
        force(
            "androidx.core:core:${libs.versions.androidxCore.get()}",
            "androidx.core:core-ktx:${libs.versions.androidxCore.get()}",
            "androidx.lifecycle:lifecycle-viewmodel-compose:${libs.versions.androidxLifecycle.get()}",
            "androidx.lifecycle:lifecycle-runtime-compose:${libs.versions.androidxLifecycle.get()}",
            "androidx.lifecycle:lifecycle-viewmodel:${libs.versions.androidxLifecycle.get()}",
            "androidx.lifecycle:lifecycle-runtime:${libs.versions.androidxLifecycle.get()}",
            "androidx.lifecycle:lifecycle-runtime-ktx:${libs.versions.androidxLifecycle.get()}",
            "androidx.lifecycle:lifecycle-viewmodel-ktx:${libs.versions.androidxLifecycle.get()}",
            "androidx.lifecycle:lifecycle-common:${libs.versions.androidxLifecycle.get()}"
        )
    }
}

val targetApk = layout.buildDirectory.file("outputs/apk/debug/androidApp-debug.apk")

tasks.register<CopyDebugApkTask>("copyDebugApkToRoot") {
    inputFile.set(targetApk)
    betaBuild.set(isBetaApp)
    rootDirectory.set(layout.projectDirectory.dir(".."))
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
