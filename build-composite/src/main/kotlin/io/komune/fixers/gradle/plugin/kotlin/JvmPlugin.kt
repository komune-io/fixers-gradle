package io.komune.fixers.gradle.plugin.kotlin

import io.komune.fixers.gradle.config.fixers
import io.komune.fixers.gradle.config.model.Jdk
import io.komune.fixers.gradle.dependencies.FixersDependencies
import io.komune.fixers.gradle.dependencies.FixersPluginVersions
import io.komune.fixers.gradle.plugin.config.ConfigPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.kotlin
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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
		logger.info("Configuring JVM compilation for project: ${name}")
		plugins.apply(ConfigPlugin::class.java)
		val fixersConfig = rootProject.extensions.fixers
		val jdkVersion = fixersConfig?.jdk?.version?.orNull ?: Jdk.VERSION_DEFAULT

		logger.info("Using JDK version: $jdkVersion")

		kotlinExtension.jvmToolchain(jdkVersion)
		tasks.withType<KotlinCompile>().configureEach {
			logger.info("Configuring Kotlin compile task: $name in project ${project.name}")
			kotlinOptions {
				freeCompilerArgs = listOf("-Xjsr305=strict")
				jvmTarget = jdkVersion.toString()
				languageVersion = FixersPluginVersions.kotlin.substringBeforeLast(".")
			}
		}

		plugins.withType(JavaPlugin::class.java).whenPluginAdded {
			tasks.withType(JavaCompile::class) {
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

		tasks.withType<Test> {
			logger.info("Configuring test task: $name in project ${project.name}")
			useJUnitPlatform()
		}
	}
}
