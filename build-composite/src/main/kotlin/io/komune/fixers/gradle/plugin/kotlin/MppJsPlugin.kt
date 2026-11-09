package io.komune.fixers.gradle.plugin.kotlin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class MppJsPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        configureJsCompilation(target)
    }

    private fun configureJsCompilation(project: Project) {
        project.extensions.configure(KotlinMultiplatformExtension::class.java) {
            js {
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
            sourceSets.getByName("jsTest") {
                dependencies {
                    implementation(kotlin("test-js"))
                }
            }
        }
    }
    // https://kotlinlang.org/docs/js-project-setup.html
    private fun KotlinJsCompilerOptions.configureJsOptions() {
        target.set("es2015")
        freeCompilerArgs.add("-Xes-long-as-bigint")
    }
}
