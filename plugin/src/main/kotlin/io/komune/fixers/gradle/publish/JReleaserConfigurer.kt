package io.komune.fixers.gradle.publish
import io.komune.gradle.config.ConfigExtension
import io.komune.gradle.config.model.Repository
import io.komune.gradle.config.model.github
import java.lang.System.getenv
import org.gradle.api.Project
import org.jreleaser.model.Signing
import org.jreleaser.gradle.plugin.JReleaserExtension
import org.jreleaser.model.Active
import org.gradle.kotlin.dsl.configure
import org.jreleaser.gradle.plugin.JReleaserPlugin

/**
 * Configures JReleaser for publishing artifacts.
 */
class JReleaserConfigurer {

    /**
     * Configures JReleaser plugin for the given project.
     *
     * @param project The project to configure JReleaser for
     * @param fixersConfig The Fixers configuration extension
     */
    fun configure(project: Project, fixersConfig: ConfigExtension) {
        project.plugins.apply(JReleaserPlugin::class.java)

        val versionFromFile = searchVersion(project)

        val repositoryName = getenv("PKG_MAVEN_REPO")
            ?: project.findProperty("PKG_MAVEN_REPO")?.toString() 
            ?: ""

        if (project.version.toString().isEmpty()) {
            project.version = versionFromFile
        }

        project.configureJReleaser(
            fixersConfig, 
            versionFromFile, 
            fixersConfig.isPromote.get(), 
            fixersConfig.isPublish.get(), 
            repositoryName
        )

        registerDeployTask(project)
    }

    /**
     * Configures the JReleaser extension with all necessary settings.
     */
    @Suppress("LongMethod")
    private fun Project.configureJReleaser(
        fixersConfig: ConfigExtension,
        versionFromFile: String,
        isPromote: Boolean,
        isPublish: Boolean,
        repositoryName: String
    ) {
        val project = this
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
                            url.set("https://central.sonatype.com/api/v1/publisher")
//                            applyMavenCentralRules.set(true)
                            snapshotSupported.set(false)
                            stagingRepository(
                                project.layout.buildDirectory.dir("staging-deploy").get().asFile.absolutePath
                            )
                        }
                    }
                    github {
                        create("GITHUB") {
                            val pkgGithubUsername = fixersConfig.pkgGithubUsername.get()
                            project.logger.lifecycle("PKG_GITHUB_USERNAME: $pkgGithubUsername")
                            val pkgGithubToken = fixersConfig.pkgGithubToken.get()
                            username.set(pkgGithubUsername)
                            password.set(pkgGithubToken)

                            active.set(if (isPublish) Active.ALWAYS else Active.NEVER)

                            val repository = fixersConfig.repositories[repositoryName]
                                ?: Repository.github(project)
                            url.set(repository.getUrl().toString())
//                            url.set(fixersConfig.githubPackagesUrl.get())
//                            applyMavenCentralRules.set(true)
                            snapshotSupported.set(true)

                            stagingRepository(
                                project.layout.buildDirectory.dir("staging-deploy").get().asFile.absolutePath
                            )
                        }
                    }
                    nexus2 {
                        create("SNAPSHOT") {
                            active.set(if (isPromote) Active.SNAPSHOT else Active.NEVER)
                            url.set("https://central.sonatype.com/repository/maven-snapshots/")
                            snapshotUrl.set("https://central.sonatype.com/repository/maven-snapshots/")
//                            applyMavenCentralRules.set(true)
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

            // Configure git root search and release
            gitRootSearch.set(true)
            release {
                github {
                    skipRelease.set(true)
                }
            }
        }
    }

    /**
     * Registers the deploy task that depends on publish and jreleaserDeploy.
     */
    private fun registerDeployTask(project: Project) {
        project.tasks.register("deploy") {
            group = "publishing"
            description = "Publishes all artifacts using JReleaser"
            dependsOn("publish", "jreleaserDeploy")
        }
    }

    /**
     * Searches for the version in the VERSION file or falls back to the project's version.
     *
     * @param project The project to search the version for
     * @return The version string
     */
    private fun searchVersion(project: Project): String {
        val versionFile = project.rootProject.file("VERSION")
        val versionFromFile = if (versionFile.exists()) {
            versionFile.readText().trim()
        } else {
            project.version.toString()
        }
        return versionFromFile
    }
}
