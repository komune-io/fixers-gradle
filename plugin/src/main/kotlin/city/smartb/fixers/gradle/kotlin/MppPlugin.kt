package city.smartb.fixers.gradle.kotlin

import city.smartb.gradle.dependencies.FixersDependencies
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getting
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.withType
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class MppPlugin : Plugin<Project> {

	override fun apply(target: Project) {
		setupMultiplatformLibrary(target)
		setupJvmTarget(target)
		setupJsTarget(target)
		target.setupMppPublishJar()
	}

	private fun setupMultiplatformLibrary(target: Project) {
		target.apply(plugin = "org.jetbrains.kotlin.multiplatform")

		target.tasks.withType<KotlinCompile>().configureEach {
			kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.js.ExperimentalJsExport"
		}

		target.extensions.configure(KotlinMultiplatformExtension::class.java) {
			sourceSets {
				maybeCreate("commonMain").dependencies {
					implementation(kotlin("reflect"))
					FixersDependencies.Common.Kotlin.coroutines(::api)
					FixersDependencies.Common.Kotlin.serialization(::api)
				}
				maybeCreate("commonTest").dependencies {
					FixersDependencies.Common.test(::implementation)
				}
			}
		}
	}

	private fun setupJvmTarget(project: Project) {
		project.kotlin {
			jvm {
				compilations.all {
					kotlinOptions.jvmTarget = "11"
				}
			}
			sourceSets.getByName("jvmMain") {
				dependencies {
					implementation(kotlin("reflect"))
					FixersDependencies.Jvm.Kotlin.coroutines(::implementation)
				}
			}
			sourceSets.getByName("jvmTest") {
				dependencies {
					FixersDependencies.Jvm.Test.junit (::implementation)
				}
			}
		}
	}

	private fun setupJsTarget(project: Project) {
		project.apply<MppJsPlugin>()
	}

	private fun Project.kotlin(action: Action<KotlinMultiplatformExtension>) {
		extensions.configure(KotlinMultiplatformExtension::class.java, action)
	}

	private fun Project.setupMppPublishJar() {
		plugins.withType(MppPlugin::class.java).whenPluginAdded {
			tasks.register("javadocJar", Jar::class.java) {
				archiveClassifier.set("javadoc")
			}
			tasks.register("sourcesJar", Jar::class.java) {
				archiveClassifier.set("sources")
			}
		}
	}

}
