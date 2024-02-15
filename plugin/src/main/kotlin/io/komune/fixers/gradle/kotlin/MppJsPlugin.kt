package io.komune.fixers.gradle.kotlin

import io.komune.gradle.dependencies.FixersPluginVersions
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class MppJsPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        configureJsCompilation(target)
    }

    private fun configureJsCompilation(target: Project) {
        target.extensions.configure(KotlinMultiplatformExtension::class.java) {
            js(IR) {
                binaries.library()
                browser {
                    testTask (
                        Action {
                            useKarma {
                                useFirefoxHeadless()
//                                useChromeHeadless()
                            }
                        }
                    )
                }
                generateTypeScriptDefinitions()
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
    }
}
