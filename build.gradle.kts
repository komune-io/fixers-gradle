import io.komune.fixers.gradle.config.fixers

plugins {
	kotlin("jvm") version embeddedKotlinVersion apply false
	alias(libs.plugins.gradlePublish) apply false
	id("composite.detekt")
	id("composite.config")
}

//tasks.withType<JavaCompile> {
//	val toolchain = the<JavaPluginExtension>().toolchain
//	val javaToolchainService = the<JavaToolchainService>()
//	toolchain.languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
//	tasks.withType<JavaExec>().configureEach {
//		javaLauncher.set(javaToolchainService.launcherFor(toolchain))
//	}
//}

allprojects {
	group = "io.komune.fixers.gradle"
	repositories {
		mavenCentral()
		gradlePluginPortal()
	}
}

fixers {
	bundle {
		id = "gradle"
		name = "Gradle Fixers"
		description = "Gradle common fixers and utilities for Komune projects"
		url = "https://github.com/komune-io/fixers-gradle"
	}
	sonar {
		organization = "komune-io"
		projectKey = "komune-io_fixers-gradle"
	}
}
