package io.komune.fixers.gradle.kotlin

import io.komune.gradle.config.fixers
import io.komune.gradle.config.model.Jdk
import io.komune.gradle.dependencies.FixersDependencies
import io.komune.gradle.dependencies.FixersPluginVersions
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.invoke
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class MppPlugin : Plugin<Project> {

	override fun apply(target: Project) {
		target.logger.info("Applying MppPlugin to project: ${target.name}")
		target.setupMultiplatformLibrary()
		target.setupJvmTarget()
		target.setupJsTarget()
		target.setupJarInfo()
	}

	private fun Project.setupMultiplatformLibrary() {
		logger.info("Setting up Multiplatform Library for project: ${name}")
		apply(plugin = "org.jetbrains.kotlin.multiplatform")
		extensions.configure(KotlinMultiplatformExtension::class.java) {
			sourceSets {
				all {
					logger.info("Add optIn[kotlin.js.ExperimentalJsExport] for $name")
					languageSettings.optIn("kotlin.js.ExperimentalJsExport")
				}
				maybeCreate("commonMain").dependencies {
					logger.info("Configuring dependencies for commonMain")
					implementation(kotlin("reflect"))
					FixersDependencies.Common.Kotlin.coroutines(::api)
					FixersDependencies.Common.Kotlin.serialization(::api)
				}
				maybeCreate("commonTest").dependencies {
					logger.info("Configuring dependencies for commonTest")
					FixersDependencies.Common.test(::implementation)
				}
			}
		}
	}

	private fun Project.setupJvmTarget() {
		logger.info("Setting up JVM Target for project: ${name}")
		val fixersConfig = rootProject.extensions.fixers
		kotlin {
			jvm {
				compilations.all {
					val jdkVersion = fixersConfig?.jdk?.version ?: Jdk.VERSION_DEFAULT
					logger.info("Configuring JVM compilation with JDK version: $jdkVersion")
					kotlinOptions.jvmTarget = jdkVersion.toString()
					kotlinOptions.languageVersion = FixersPluginVersions.kotlin.substringBeforeLast(".")
				}
			}
			sourceSets.getByName("jvmMain") {
				dependencies {
					logger.info("Configuring dependencies for jvmMain")
					implementation(kotlin("reflect"))
					FixersDependencies.Jvm.Kotlin.coroutines(::implementation)
				}
			}
			sourceSets.getByName("jvmTest") {
				dependencies {
					logger.info("Configuring dependencies for jvmTest")
					FixersDependencies.Jvm.Test.junit(::implementation)
				}
			}
		}
	}

	private fun Project.setupJsTarget() {
		logger.info("Setting up JS Target for project: ${name}")
		apply<MppJsPlugin>()
	}

	private fun Project.kotlin(action: Action<KotlinMultiplatformExtension>) {
		extensions.configure(KotlinMultiplatformExtension::class.java, action)
	}
}
