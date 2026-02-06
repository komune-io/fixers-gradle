import io.komune.fixers.gradle.config.fixers

plugins {
	kotlin("jvm") version embeddedKotlinVersion apply false
	alias(libs.plugins.gradlePublish) apply false
	id("composite.config")
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
		id = "gradle"
		name = "Gradle Fixers"
		description = "Gradle common fixers and utilities for Komune projects"
		url = "https://github.com/komune-io/fixers-gradle"
	}
	sonar {
		organization = "komune-io"
		projectKey = "komune-io_fixers-gradle"
		properties {
			property("sonar.coverage.exclusions", "config/**,dependencies/**,plugin/**")
		}
	}
}


tasks.register<Copy>("copyConfigSources") {
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

tasks.register<Copy>("copyDependenciesSources") {
	group = "fixers"
	description = "Copy dependencies sources to the dependencies module"
	logger.lifecycle("Copying dependencies sources")
	from("build-composite/src/main/kotlin/io/komune/fixers/gradle/dependencies")
	into("dependencies/src/main/kotlin/io/komune/fixers/gradle/dependencies")
}

tasks.register<Delete>("cleanDependenciesSources") {
	group = "fixers"
	description = "Clean dependencies sources from the dependencies module"
	delete("dependencies/src/main/kotlin/io/komune/fixers/gradle/dependencies")
}

tasks.register<Copy>("copyPluginSources") {
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
	project(":config") {
		tasks.named("clean") {
			dependsOn(rootProject.tasks.named("cleanConfigSources"))
		}
		tasks.named("compileKotlin") {
			dependsOn(rootProject.tasks.named("copyConfigSources"))
		}
		tasks.named("sourcesJar") {
			dependsOn(rootProject.tasks.named("copyConfigSources"))
		}
		tasks.named("detekt") {
			dependsOn(rootProject.tasks.named("copyConfigSources"))
		}
	}
	project(":dependencies") {
		tasks.named("clean") {
			dependsOn(rootProject.tasks.named("cleanDependenciesSources"))
		}
		tasks.named("compileKotlin") {
			dependsOn(rootProject.tasks.named("copyDependenciesSources"))
		}
		tasks.named("sourcesJar") {
			dependsOn(rootProject.tasks.named("copyDependenciesSources"))
		}
		tasks.named("detekt") {
			dependsOn(rootProject.tasks.named("copyDependenciesSources"))
		}
	}

	project(":plugin") {
		tasks.named("clean") {
			dependsOn(rootProject.tasks.named("cleanPluginSources"))
		}
		tasks.named("compileKotlin") {
			dependsOn(rootProject.tasks.named("copyPluginSources"))
		}
		tasks.named("sourcesJar") {
			dependsOn(rootProject.tasks.named("copyPluginSources"))
		}
		tasks.named("detekt") {
			dependsOn(rootProject.tasks.named("copyPluginSources"))
		}
	}
}
