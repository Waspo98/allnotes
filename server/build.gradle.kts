plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass.set("app.overbay.allnotes.server.ApplicationKt")
}

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named<Sync>("installDist") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<Tar>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<Zip>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

