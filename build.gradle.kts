import io.komune.fixers.gradle.config.fixers

plugins {
	kotlin("jvm") version embeddedKotlinVersion apply false
	alias(libs.plugins.gradlePublish) apply false
	id("composite.config")
	id("composite.publishing")
}

allprojects {
	group = "io.komune.fixers.gradle"
	repositories {
		mavenCentral()
		gradlePluginPortal()
	}
}

fixers {
	bundle {
		name = "Gradle Fixers"
		description = "Gradle common fixers and utilities for Komune projects"
		url = "https://github.com/komune-io/fixers-gradle"
	}
	sonar {
		organization = "komune-io"
		projectKey = "komune-io_fixers-gradle"
		properties {
			property("sonar.coverage.exclusions", "config/**,plugin/**")
		}
	}
}


// Sync, not Copy: the destination must mirror the source exactly, otherwise a file
// deleted or renamed in build-composite leaves a stale copy behind that still
// compiles and ships in the published jar.
tasks.register<Sync>("copyConfigSources") {
	group = "fixers"
	description = "Copy configuration sources to the config module"
	logger.lifecycle("Copying configuration sources")
	from("build-composite/src/main/kotlin/io/komune/fixers/gradle/config")
	into("config/src/main/kotlin/io/komune/fixers/gradle/config")
}

tasks.register<Delete>("cleanConfigSources") {
	group = "fixers"
	description = "Clean configuration sources from the config module"
	logger.lifecycle("Cleaning configuration sources")
	delete("config/src/main/kotlin/io/komune/fixers/gradle/config")
}

tasks.register<Sync>("copyPluginSources") {
	group = "fixers"
	description = "Copy plugin sources to the plugin module"
	logger.lifecycle("Copying plugin sources")
	from("build-composite/src/main/kotlin/io/komune/fixers/gradle/plugin")
	into("plugin/src/main/kotlin/io/komune/fixers/gradle/plugin")
}

tasks.register<Delete>("cleanPluginSources") {
	group = "fixers"
	description = "Clean plugin sources from the plugin module"
	delete("plugin/src/main/kotlin/io/komune/fixers/gradle/plugin")
}


gradle.projectsEvaluated {
	mapOf(":config" to "Config", ":plugin" to "Plugin").forEach { (projectPath, sourceKind) ->
		project(projectPath) {
			tasks.named("clean") {
				dependsOn(rootProject.tasks.named("clean${sourceKind}Sources"))
			}
			listOf("compileKotlin", "sourcesJar", "detekt").forEach { taskName ->
				tasks.named(taskName) {
					dependsOn(rootProject.tasks.named("copy${sourceKind}Sources"))
				}
			}
		}
	}
}
