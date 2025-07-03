package io.komune.fixers.gradle.publish

import io.komune.fixers.gradle.config.ConfigExtension
import org.gradle.api.Project
import org.jreleaser.model.Signing
import org.jreleaser.gradle.plugin.JReleaserExtension
import org.jreleaser.model.Active
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jreleaser.gradle.plugin.JReleaserPlugin
import org.jreleaser.gradle.plugin.dsl.deploy.maven.MavenDeployer

/**
 * Configures JReleaser for publishing artifacts.
 */
class JReleaserConfigurer {

    /**
     * Configures JReleaser plugin for the given project.
     *
     * @param project The project to configure JReleaser for
     * @param fixersConfig The configuration extension
     */
    fun configure(project: Project, fixersConfig: ConfigExtension) {
        project.plugins.apply(JReleaserPlugin::class.java)

        val versionFromFile = fixersConfig.version.get()

        val isPublish = fixersConfig.isPublish.get()
        val isPromote = fixersConfig.isPromote.get()

        if (project.version.toString().isEmpty()) {
            project.version = versionFromFile
        }

        configureJReleaser(project, fixersConfig, versionFromFile, isPromote, isPublish)
        registerDeployTask(project)
    }

    /**
     * Configures the JReleaser extension with all necessary settings.
     */
    @Suppress("LongMethod")
    private fun configureJReleaser(
        project: Project,
        fixersConfig: ConfigExtension,
        versionFromFile: String,
        isPromote: Boolean,
        isPublish: Boolean
    ) {
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
                            url.set(fixersConfig.mavenCentralUrl.get())
                            applyMavenCentralRules.set(true)
                            snapshotSupported.set(false)
                            stagingRepository(
                                project.layout.buildDirectory.dir("staging-deploy").get().asFile.absolutePath
                            )

                            workAroundJarFileNotFound(project)
                        }
                    }
                    github {
                        create("GITHUB") {
                            active.set(if (isPublish) Active.ALWAYS else Active.NEVER)
                            val pkgGithubUsername = fixersConfig.pkgGithubUsername.get()
                            val pkgGithubToken = fixersConfig.pkgGithubToken.get()
                            username.set(pkgGithubUsername)
                            password.set(pkgGithubToken)

                            url.set(fixersConfig.githubPackagesUrl.get())
                            applyMavenCentralRules.set(true)
                            snapshotSupported.set(true)

                            stagingRepository(
                                project.layout.buildDirectory.dir("staging-deploy").get().asFile.absolutePath
                            )
                            workAroundJarFileNotFound(project)
                        }
                    }
                    nexus2 {
                        create("SNAPSHOT") {
                            active.set(if (isPromote) Active.SNAPSHOT else Active.NEVER)
                            url.set(fixersConfig.mavenSnapshotsUrl.get())
                            snapshotUrl.set(fixersConfig.mavenSnapshotsUrl.get())
                            snapshotSupported.set(true)
                            closeRepository.set(true)
                            applyMavenCentralRules.set(true)
                            stagingRepository(
                                project.layout.buildDirectory.dir("staging-deploy").get().asFile.absolutePath
                            )
                            workAroundJarFileNotFound(project)
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
    }

    // workaround: https://github.com/jreleaser/jreleaser/issues/1784
    @Suppress("MaxLineLength")
    private fun MavenDeployer.workAroundJarFileNotFound(project: Project) {
        val kotlinExt = project.extensions.findByType(KotlinMultiplatformExtension::class.java)
        kotlinExt?.targets?.forEach  { target ->
            if (target !is KotlinJvmTarget) {
                val nonJarArtifactId = if (target.platformType == KotlinPlatformType.wasm) {
                    "${project.name}-wasm-${target.name.lowercase().substringAfter("wasm")}"
                } else {
                    "${project.name}-${target.name.lowercase()}"
                }
                artifactOverride {
                    artifactId.set(nonJarArtifactId)
                    jar.set(false)
                    verifyPom.set(false)
                    sourceJar.set(false)
                    javadocJar.set(false)
                }
            }
        }
    }

    /**
     * Registers the deploy task that depends on publish and jreleaserDeploy.
     * Adds a doFirst action to handle potential missing files gracefully.
     */
    private fun registerDeployTask(project: Project) {
        project.tasks.register("deploy") {
            group = "publishing"
            description = "Publishes all artifacts using JReleaser"
            dependsOn("publish", "jreleaserDeploy")
        }
    }
}
