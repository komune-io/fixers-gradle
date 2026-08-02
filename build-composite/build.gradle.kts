plugins {
    `kotlin-dsl`
    jacoco
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

val generateToolVersions by tasks.registering {
    val jacocoVersion = libs.versions.jacoco.get()
    val outputDir = layout.buildDirectory.dir("generated/fixers/kotlin")
    inputs.property("jacocoVersion", jacocoVersion)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("io/komune/fixers/gradle/config/model/FixersToolVersions.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package io.komune.fixers.gradle.config.model
            |
            |/**
            | * Tool versions generated from gradle/libs.versions.toml.
            | */
            |object FixersToolVersions {
            |    const val jacoco = "$jacocoVersion"
            |}
            |""".trimMargin()
        )
    }
}

kotlin.sourceSets.named("main") {
    kotlin.srcDir(generateToolVersions)
}

dependencies {
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))

    implementation(kotlin("gradle-plugin", embeddedKotlinVersion))

    implementation(libs.detektGradlePlugin)
    implementation(libs.mavenPublishGradlePlugin)
    implementation(libs.npmPublishGradlePlugin)
    implementation(libs.sonarqubeGradlePlugin)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(gradleTestKit())
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.assertj.core.specific)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // Unit tests must be deterministic regardless of ambient release credentials:
    // FIXERS_* env vars are read as property conventions by the config models.
    setEnvironment(environment.filterKeys { !it.startsWith("FIXERS_") })
    testLogging {
        events("passed", "skipped", "failed")
    }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
