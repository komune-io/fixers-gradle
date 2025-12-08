package io.komune.fixers.gradle.plugin.kotlin

import io.komune.fixers.gradle.config.fixers
import io.komune.fixers.gradle.config.model.Jdk
import io.komune.fixers.gradle.dependencies.FixersDependencies
import io.komune.fixers.gradle.dependencies.FixersPluginVersions
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.invoke
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

class MppPlugin : Plugin<Project> {

	override fun apply(target: Project) {
		target.logger.info("Applying MppPlugin to project: ${target.name}")
		target.setupMultiplatformLibrary()
		target.setupJvmTarget()
		target.setupJsTarget()
		target.setupJarInfo()
	}

	private fun Project.setupMultiplatformLibrary() {
		logger.info("Setting up Multiplatform Library for project: $name")
		apply(plugin = "org.jetbrains.kotlin.multiplatform")
		extensions.configure(KotlinMultiplatformExtension::class.java) {
			sourceSets {
				configureEach {
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
		logger.info("Setting up JVM Target for project: $name")
		val fixersConfig = rootProject.extensions.fixers
		val jdkVersion = fixersConfig?.jdk?.version?.orNull ?: Jdk.VERSION_DEFAULT
		kotlin {
			jvm {
				logger.info("Configuring JVM compilation with JDK version: $jdkVersion")
				compilations.all {
					compileTaskProvider.configure {
						compilerOptions {
							jvmTarget.set(JvmTarget.fromTarget(jdkVersion.toString()))
							val kotlinLangVersion = FixersPluginVersions.kotlin.substringBeforeLast(".")
							languageVersion.set(KotlinVersion.fromVersion(kotlinLangVersion))
						}
					}
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
		logger.info("Setting up JS Target for project: $name")
		apply<MppJsPlugin>()
	}

	private fun Project.kotlin(action: Action<KotlinMultiplatformExtension>) {
		extensions.configure(KotlinMultiplatformExtension::class.java, action)
	}
}
