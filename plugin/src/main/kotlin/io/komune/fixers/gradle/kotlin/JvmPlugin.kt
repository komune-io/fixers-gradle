package io.komune.fixers.gradle.kotlin

import io.komune.fixers.gradle.config.ConfigPlugin
import io.komune.gradle.config.fixers
import io.komune.gradle.config.model.Jdk
import io.komune.gradle.dependencies.FixersDependencies
import io.komune.gradle.dependencies.FixersPluginVersions
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.kotlin
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

@Suppress("UnstableApiUsage")
class JvmPlugin : Plugin<Project> {

	override fun apply(target: Project) {
		configureJvmCompilation(target)
		target.setupJarInfo()
	}

	private fun configureJvmCompilation(target: Project) {
		target.apply(plugin = "java")
		target.apply(plugin = "org.jetbrains.kotlin.jvm")
		target.plugins.apply(ConfigPlugin::class.java)
		val fixersConfig = target.rootProject.extensions.fixers
		val jdkVersion = fixersConfig?.jdk?.version ?: Jdk.VERSION_DEFAULT

		target.tasks.withType<KotlinCompile>().configureEach {
			println("Configuring $name in project ${project.name}...")
			kotlinOptions {
				freeCompilerArgs = listOf("-Xjsr305=strict",  "-opt-in=kotlin.js.ExperimentalJsExport")
				jvmTarget = jdkVersion.toString()
				languageVersion = FixersPluginVersions.kotlin.substringBeforeLast(".")
			}
		}

		target.plugins.withType(JavaPlugin::class.java).whenPluginAdded {
			target.extensions.configure(JavaPluginExtension::class.java) {
				toolchain.languageVersion.set(JavaLanguageVersion.of(jdkVersion))
			}
		}


		target.dependencies {
			add("implementation", kotlin("reflect"))
			FixersDependencies.Jvm.Kotlin.coroutines{
				add("implementation", it)
			}
			FixersDependencies.Jvm.Test.junit{
				add("testImplementation", it)
			}
		}

		target.tasks.withType<Test> {
			useJUnitPlatform()
		}
	}
}
