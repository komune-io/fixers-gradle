plugins {
	kotlin("jvm") version embeddedKotlinVersion apply false
	alias(libs.plugins.gradlePublish) apply false
	id("composite.detekt")
	id("composite.config")
}

tasks.withType<JavaCompile> {
	val toolchain = the<JavaPluginExtension>().toolchain
	val javaToolchainService = the<JavaToolchainService>()
	toolchain.languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
	tasks.withType<JavaExec>().configureEach {
		javaLauncher.set(javaToolchainService.launcherFor(toolchain))
	}
}

allprojects {
	val versionFile = rootProject.file("VERSION")
	version = if (versionFile.exists()) {
		versionFile.readText().trim()
	} else {
		System.getenv("VERSION") ?: "experimental-SNAPSHOT"
	}
	group = "io.komune.fixers.gradle"
	description = "Gradle common fixers and utilities for Komune projects"
	repositories {
		mavenCentral()
		gradlePluginPortal()
	}
}


subprojects {
	tasks.withType<Jar> {
		manifest {
			attributes(
				"Implementation-Title" to project.name,
				"Implementation-Version" to project.version
			)
		}
	}
}
