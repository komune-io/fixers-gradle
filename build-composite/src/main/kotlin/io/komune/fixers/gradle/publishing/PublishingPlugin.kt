package io.komune.fixers.gradle.publishing

import io.komune.fixers.gradle.config.ConfigExtension
import io.komune.fixers.gradle.config.PkgDeployType
import io.komune.fixers.gradle.config.PkgMavenRepo
import io.komune.fixers.gradle.config.config
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.PublishingExtension as GradlePublishingExtension
import org.gradle.plugins.signing.SigningExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.plugins.signing.SigningPlugin
import org.jreleaser.model.Signing
import org.jreleaser.gradle.plugin.JReleaserExtension
import org.jreleaser.gradle.plugin.JReleaserPlugin
import org.jreleaser.model.Active

/**
 * Consolidated plugin that replaces all the separate publishing plugins.
 * It can be configured for different types of projects (module, plugin, jreleaser).
 */
class PublishingPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.apply(MavenPublishPlugin::class.java)
        project.plugins.apply(SigningPlugin::class.java)
        project.plugins.apply(JReleaserPlugin::class.java)

        val extension = project.publishing()
        val configExtension = project.config()

        val hasPublishPlugin = project.plugins.hasPlugin("com.gradle.plugin-publish")
        val hasJReleaserPlugin = project.plugins.hasPlugin("org.jreleaser")

        project.afterEvaluate {
            project.extensions.configure<GradlePublishingExtension>("publishing") {
                publications {
                    configureMavenPublications(project, extension, configExtension)
                }
                repositories {
                    maven {
                        url = project.uri(project.layout.buildDirectory.dir("staging-deploy"))
                    }
                }
            }

            project.extensions.configure<SigningExtension>("signing") {
                useInMemoryPgpKeys(
                    extension.signingKey.get(),
                    extension.signingPassword.get()
                )

                if (!hasPublishPlugin) {
                    val publishing = project.extensions.getByType<GradlePublishingExtension>()
                    sign(publishing.publications.getByName("mavenJava"))
                }
            }
        }

        if (hasJReleaserPlugin) {
            configureJReleasePlugin(project, configExtension)
        }
    }

    private fun configureJReleasePlugin(project: Project, configExtension: ConfigExtension) {
        project.plugins.apply("org.jreleaser")

        val versionFromFile = project.searchVersion()

        val pkgDeployType = configExtension.pkgDeployType.get()
        val ispkgDeployTypePromote = pkgDeployType == PkgDeployType.PROMOTE
        val ispkgDeployTypePublish = pkgDeployType == PkgDeployType.PUBLISH

        val isGithubMavenRepo = configExtension.pkgMavenRepo.get() == PkgMavenRepo.GITHUB
        val isNotGithubMavenRepo = !isGithubMavenRepo

        val isPromote = ispkgDeployTypePromote || isNotGithubMavenRepo
        val isPublish = ispkgDeployTypePublish || isGithubMavenRepo

        if (project.version.toString().isEmpty()) {
            project.version = versionFromFile
        }

        project.extensions.configure<JReleaserExtension> {
            project {
                version.set(versionFromFile)
            }
            signing {
                active.set(Active.ALWAYS)
                armored.set(true)
                mode.set(Signing.Mode.COSIGN)
            }
            deploy {
                maven {
                    mavenCentral {
                        create("MAVENCENTRAL") {
                            active.set(if (isPromote) Active.RELEASE else Active.NEVER)
                            url.set(configExtension.mavenCentralUrl.get())
                            applyMavenCentralRules.set(true)
                            snapshotSupported.set(false)
                            stagingRepository(
                                project.layout.buildDirectory.dir("staging-deploy").get().asFile.absolutePath
                            )
                        }
                    }
                    github {
                        create("GITHUB") {
                            val pkgGithubUsername = configExtension.pkgGithubUsername.get()
                            project.logger.lifecycle("PKG_GITHUB_USERNAME: $pkgGithubUsername")
                            val pkgGithubToken = configExtension.pkgGithubToken.get()

                            username.set(pkgGithubUsername)
                            password.set(pkgGithubToken)

                            active.set(if (isPublish) Active.ALWAYS else Active.NEVER)

                            url.set(configExtension.githubPackagesUrl.get())
                            applyMavenCentralRules.set(true)
                            snapshotSupported.set(true)

                            stagingRepository(
                                project.layout.buildDirectory.dir("staging-deploy").get().asFile.absolutePath
                            )
                        }
                    }
                    nexus2 {
                        create("SNAPSHOT") {
                            active.set(if (isPromote) Active.SNAPSHOT else Active.NEVER)
                            url.set(configExtension.mavenSnapshotsUrl.get())
                            snapshotUrl.set(configExtension.mavenSnapshotsUrl.get())
                            applyMavenCentralRules.set(true)
                            snapshotSupported.set(true)
                            closeRepository.set(true)
                            releaseRepository.set(true)
                            stagingRepository(
                                project.layout.buildDirectory.dir("staging-deploy").get().asFile.absolutePath
                            )
                        }
                    }
                }
            }
            gitRootSearch.set(true)
            release {
                github {
                    skipRelease.set(true)
                }
            }
        }

        project.tasks.register("deploy") {
            group = "publishing"
            description = "Publishes all plugin marker artifacts (skipping JReleaser due to configuration issues)"
            dependsOn("publish", "jreleaserDeploy")
        }
    }

}

private fun Project.searchVersion(): String {
    val versionFile = rootProject.file("VERSION")
    val versionFromFile = if (versionFile.exists()) {
        versionFile.readText().trim()
    } else {
        project.version.toString()
    }
    return versionFromFile
}
