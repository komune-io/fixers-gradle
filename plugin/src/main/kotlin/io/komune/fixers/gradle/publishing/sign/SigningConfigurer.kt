package io.komune.fixers.gradle.publishing.sign

import io.komune.fixers.gradle.publishing.PublishingExtension
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension as GradlePublishingExtension
import org.gradle.plugins.signing.SigningExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType

/**
 * Configurer for signing in the publishing plugin.
 *
 * This class is responsible for configuring the signing extension
 * to sign Maven publications.
 */
class SigningConfigurer {
    /**
     * Configures the signing extension.
     * 
     * @param project The Gradle project
     * @param extension The PublishingExtension
     * @param isPlugin Whether the project is a Gradle plugin
     * @throws IllegalStateException if signing key or password is missing
     */
    fun configureSigning(project: Project, extension: PublishingExtension, isPlugin: Boolean) {
        check(extension.signingKey.isPresent && extension.signingKey.get().isNotEmpty()) {
            "Signing key is required for publishing. " +
            "Set it using the GPG_SIGNING_KEY environment variable or in the build script."
        }

        check(extension.signingPassword.isPresent && extension.signingPassword.get().isNotEmpty()) {
            "Signing password is required for publishing. " +
            "Set it using the GPG_SIGNING_PASSWORD environment variable or in the build script."
        }

        try {
            project.extensions.configure<SigningExtension>("signing") {
                useInMemoryPgpKeys(
                    extension.signingKey.get(),
                    extension.signingPassword.get()
                )

                if (!isPlugin) {
                    val publishing = project.extensions.getByType<GradlePublishingExtension>()
                    try {
                        sign(publishing.publications.getByName("mavenJava"))
                    } catch (e: Exception) {
                        project.logger.error("Failed to sign mavenJava publication: ${e.message}")
                        error("Failed to sign mavenJava publication: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            project.logger.error("Failed to configure signing: ${e.message}")
            error("Failed to configure signing: ${e.message}")
        }
    }
}
