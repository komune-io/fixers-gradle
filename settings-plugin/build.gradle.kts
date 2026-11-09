plugins {
	`kotlin-dsl`
	id("com.gradle.plugin-publish")
	id("io.komune.fixers.gradle.publish")
}

// No dependency beyond the Gradle API: the plugin is applied to settings, and anything it brings
// lands on the classpath of every build script. With the Kotlin Gradle plugin there, each
// `kotlin("…") version "…"` request fails as already on the classpath with an unknown version.
dependencies {
	testImplementation(libs.bundles.test)
	testImplementation(gradleTestKit())
	testRuntimeOnly(libs.junit.platform.launcher)
}

val generateSettingsVersion = tasks.register("generateSettingsVersion") {
	val pluginVersion = provider { project.version.toString() }
	val outputDir = layout.buildDirectory.dir("generated/fixers/kotlin")
	inputs.property("version", pluginVersion)
	outputs.dir(outputDir)
	doLast {
		val file = outputDir.get().file("io/komune/fixers/gradle/settings/FixersSettingsVersion.kt").asFile
		file.parentFile.mkdirs()
		file.writeText(
			"""
			|package io.komune.fixers.gradle.settings
			|
			|/**
			| * Version of this plugin, which is also the version of the f2 catalog it loads.
			| */
			|internal object FixersSettingsVersion {
			|    const val VALUE = "${pluginVersion.get()}"
			|}
			|""".trimMargin()
		)
	}
}

kotlin.sourceSets.named("main") {
	kotlin.srcDir(generateSettingsVersion)
}

gradlePlugin {
	website = "https://github.com/komune-io/fixers-gradle"
	vcsUrl = "https://github.com/komune-io/fixers-gradle"
	plugins {
		create("io.komune.fixers.gradle.settings") {
			id = "io.komune.fixers.gradle.settings"
			implementationClass = "io.komune.fixers.gradle.settings.SettingsPlugin"
			displayName = "Komune FixersGradle Settings"
			description = "Settings plugin that loads the f2 version catalog as `catalogue` and declares the Node.js and Yarn distribution repositories of Kotlin/JS."
			tags = listOf("Komune", "Fixers", "kotlin", "settings", "catalog", "js")
		}
	}
}

gradlePlugin.plugins.configureEach {
	(this as ExtensionAware).extensions.configure<org.gradle.plugin.compatibility.CompatibilityExtension>("compatibility") {
		features {
			configurationCache.set(false)
		}
	}
}
