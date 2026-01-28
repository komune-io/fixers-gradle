package io.komune.fixers.gradle.plugin.kotlin

import io.komune.fixers.gradle.config.fixers
import io.komune.fixers.gradle.config.model.Jdk
import io.komune.fixers.gradle.config.utils.configureJUnitPlatform
import io.komune.fixers.gradle.dependencies.FixersDependencies
import io.komune.fixers.gradle.dependencies.FixersPluginVersions
import io.komune.fixers.gradle.plugin.config.ConfigPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.kotlin
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

@Suppress("UnstableApiUsage")
class JvmPlugin : Plugin<Project> {

	override fun apply(target: Project) {
		target.logger.info("Applying JvmPlugin to project: ${target.name}")
		target.apply(plugin = "java")
		target.apply(plugin = "org.jetbrains.kotlin.jvm")
		target.configureJvmCompilation()
		target.setupJarInfo()
	}

	private fun Project.configureJvmCompilation() {
		logger.info("Configuring JVM compilation for project: $name")
		plugins.apply(ConfigPlugin::class.java)
		val fixersConfig = rootProject.extensions.fixers
		val jdkVersion = fixersConfig?.jdk?.version?.orNull ?: Jdk.VERSION_DEFAULT

		logger.info("Using JDK version: $jdkVersion")

		// Configure compiler options at extension level (recommended approach for Kotlin 2.x)
		extensions.configure(KotlinJvmProjectExtension::class.java) {
			jvmToolchain(jdkVersion)
			compilerOptions {
				freeCompilerArgs.add("-Xjsr305=strict")
				jvmTarget.set(JvmTarget.fromTarget(jdkVersion.toString()))
				val kotlinLangVersion = FixersPluginVersions.kotlin.substringBeforeLast(".")
				languageVersion.set(KotlinVersion.fromVersion(kotlinLangVersion))
			}
		}

		plugins.withType(JavaPlugin::class.java).whenPluginAdded {
			tasks.withType(JavaCompile::class).configureEach {
				logger.info("Configuring Java compile task: $name in project ${project.name}")
				options.release.set(jdkVersion)
			}
			extensions.configure(JavaPluginExtension::class.java) {
				toolchain.languageVersion.set(JavaLanguageVersion.of(jdkVersion))
			}
		}

		dependencies {
			logger.info("Configuring dependencies for project: $name")
			add("implementation", kotlin("reflect"))
			FixersDependencies.Jvm.Kotlin.coroutines {
				add("implementation", it)
			}
			FixersDependencies.Jvm.Test.junit {
				add("testImplementation", it)
			}
		}

		configureJUnitPlatform()
	}
}
