package io.komune.fixers.gradle.plugin.kotlin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.ExperimentalJsTestDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

@OptIn(ExperimentalJsTestDsl::class)
class MppJsPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        configureJsCompilation(target)
        target.refuseJsExportIgnore()
    }

    private fun configureJsCompilation(project: Project) {
        project.extensions.configure(KotlinMultiplatformExtension::class.java) {
            js {
                binaries.library()
                browser {
                    // Playwright downloads the browsers it drives, so the tests run in a real
                    // Chromium and Firefox without a browser installed on the build machine.
                    test {
                        chromium()
                        firefox()
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
