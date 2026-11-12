plugins {
    `kotlin-dsl`
    id("io.komune.fixers.gradle.kotlin.jvm")
    id("io.komune.fixers.gradle.publish")
}

project.plugins.withId("java-gradle-plugin") { // only do it if it's actually applied
    project.configure<GradlePluginDevelopmentExtension> {
        isAutomatedPublishing = false
    }
}

val generateToolVersions = tasks.register("generateToolVersions") {
    val jacocoVersion = libs.versions.jacoco.get()
    val bouncyCastleVersion = libs.versions.bouncycastle.get()
    val npmWsVersion = libs.versions.npm.ws.get()
    val npmAjvVersion = libs.versions.npm.ajv.get()
    val outputDir = layout.buildDirectory.dir("generated/fixers/kotlin")
    inputs.property("jacocoVersion", jacocoVersion)
    inputs.property("bouncyCastleVersion", bouncyCastleVersion)
    inputs.property("npmWsVersion", npmWsVersion)
    inputs.property("npmAjvVersion", npmAjvVersion)
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
            |    const val bouncyCastle = "$bouncyCastleVersion"
            |    const val npmWs = "$npmWsVersion"
            |    const val npmAjv = "$npmAjvVersion"
            |}
            |""".trimMargin()
        )
    }
}

kotlin.sourceSets.named("main") {
    kotlin.srcDir(generateToolVersions)
}
