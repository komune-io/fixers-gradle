package io.komune.fixers.gradle.plugin.kotlin

import io.komune.fixers.gradle.dependencies.FixersPluginVersions
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrLink
import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile

class MppJsPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        configureJsCompilation(target)
    }

    private fun configureJsCompilation(project: Project) {
        project.extensions.configure(KotlinMultiplatformExtension::class.java) {
            js(IR) {
                binaries.library()
                browser {
                    testTask {
                        useKarma {
                            useFirefoxHeadless()
                        }
                    }
                }
                generateTypeScriptDefinitions()
                compilerOptions {
                    configureJsOptions()
                }
            }
            sourceSets.getByName("jsMain") {
                dependencies {
                }
            }
            sourceSets.getByName("jsTest") {
                dependencies {
                    implementation("org.jetbrains.kotlin:kotlin-test-js:${FixersPluginVersions.kotlin}")
                }
            }
        }

        // Configure Kotlin JS compile and link tasks
        project.tasks.withType(Kotlin2JsCompile::class.java).configureEach {
            compilerOptions.configureJsOptions()
        }
        project.tasks.withType(KotlinJsIrLink::class.java).configureEach {
            compilerOptions.configureJsOptions()
        }
    }
    // https://kotlinlang.org/docs/js-project-setup.html
    private fun KotlinJsCompilerOptions.configureJsOptions() {
        target.set("es2015")
        freeCompilerArgs.add("-Xes-long-as-bigint")
        freeCompilerArgs.add("-Xenable-suspend-function-exporting")
    }
}
