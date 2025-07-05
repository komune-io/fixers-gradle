package io.komune.fixers.gradle.plugin.publishing

import io.komune.fixers.gradle.config.ConfigExtension
import io.komune.fixers.gradle.config.config
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.PublishingExtension as GradlePublishingExtension
import org.gradle.plugins.signing.SigningExtension
import org.gradle.kotlin.dsl.getByType
import org.gradle.plugins.signing.SigningPlugin

/**
 * Consolidated plugin that replaces all the separate publishing plugins.
 * It can be configured for different types of projects (module, plugin, jreleaser).
 */
class PublishingPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.apply(MavenPublishPlugin::class.java)
        project.plugins.apply(SigningPlugin::class.java)

        val configExtension = project.config()

        val hasPublishPlugin = project.plugins.hasPlugin("com.gradle.plugin-publish")
        val publishConfiguration = project.publishing()
        project.afterEvaluate {
            project.extensions.configure<GradlePublishingExtension>("publishing") {
                publications {
                    configureMavenPublications(project, publishConfiguration, configExtension)
                }
                repositories {
                    maven {
                        url = project.uri(project.layout.buildDirectory.dir("staging-deploy"))
                    }
                }
            }

            project.extensions.configure<SigningExtension>("signing") {
                useInMemoryPgpKeys(
                    publishConfiguration.signingKey.get(),
                    publishConfiguration.signingPassword.get()
                )

                if (!hasPublishPlugin) {
                    val publishing = project.extensions.getByType<GradlePublishingExtension>()
                    sign(publishing.publications.getByName("mavenJava"))
                }
            }
        }

        configureJReleasePlugin(project, publishConfiguration, configExtension)
    }

    private fun configureJReleasePlugin(project: Project, publishConfiguration: PublishConfiguration, configExtension: ConfigExtension) {
        JReleaserConfigurer(publishConfiguration).configure(project, configExtension)
    }
}
