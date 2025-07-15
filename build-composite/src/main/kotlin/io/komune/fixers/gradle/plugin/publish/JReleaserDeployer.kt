package io.komune.fixers.gradle.plugin.publish

import io.komune.fixers.gradle.config.ConfigExtension
import io.komune.fixers.gradle.config.model.PkgDeployType
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jreleaser.gradle.plugin.JReleaserExtension
import org.jreleaser.gradle.plugin.JReleaserPlugin
import org.jreleaser.gradle.plugin.dsl.deploy.maven.MavenDeployer
import org.jreleaser.model.Active
import org.jreleaser.model.Signing

/**
 * Configures JReleaser for publishing artifacts.
 */
object JReleaserDeployer {

    /**
     * Configures JReleaser plugin for the given project.
     */
    fun configure(project: Project, fixersConfig: ConfigExtension) {
        project.plugins.apply(JReleaserPlugin::class.java)

        val versionFromFile = fixersConfig.bundle.version.get()
        if (project.version.toString().isEmpty()) {
            project.version = versionFromFile
        }

        configureJReleaser(project, fixersConfig)
        registerDeployTask(project, fixersConfig)
    }

    /**
     * Configures the JReleaser extension with all necessary settings.
     */
    @Suppress("LongMethod")
    private fun configureJReleaser(
        project: Project,
        fixersConfig: ConfigExtension
    ) {
        project.extensions.configure<JReleaserExtension> {
            project {
                version.set(fixersConfig.bundle.version)
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
                            // IMPORTANT: Use project.provider to defer the decision until execution time
                            active.set(project.provider {
                                if (fixersConfig.publish.isPromote.get()) Active.RELEASE else Active.NEVER
                            })
                            url.set(fixersConfig.publish.mavenCentralUrl)
                            applyMavenCentralRules.set(true)
                            snapshotSupported.set(false)
                            stagingRepository(
                                project.layout.buildDirectory.dir(fixersConfig.publish.stagingDirectory.get()).get().asFile.absolutePath
                            )
                            workAroundJarFileNotFound(project)
                        }
                    }
                    github {
                        create("GITHUB") {
                            // IMPORTANT: Use project.provider to defer the decision until execution time
                            active.set(project.provider {
                                if (fixersConfig.publish.isStage.get()) Active.ALWAYS else Active.NEVER
                            })
                            username.set(fixersConfig.publish.pkgGithubUsername)
                            password.set(fixersConfig.publish.pkgGithubToken)
                            url.set(fixersConfig.githubPackagesUrl)
                            applyMavenCentralRules.set(true)
                            snapshotSupported.set(true)
                            stagingRepository(
                                project.layout.buildDirectory.dir(fixersConfig.publish.stagingDirectory.get()).get().asFile.absolutePath
                            )
                            workAroundJarFileNotFound(project)
                        }
                    }
                    nexus2 {
                        create("SNAPSHOT") {
                            // IMPORTANT: Use project.provider to defer the decision until execution time
                            active.set(project.provider {
                                if (fixersConfig.publish.isPromote.get()) Active.SNAPSHOT else Active.NEVER
                            })
                            url.set(fixersConfig.publish.mavenSnapshotsUrl)
                            snapshotUrl.set(fixersConfig.publish.mavenSnapshotsUrl)
                            snapshotSupported.set(true)
                            closeRepository.set(true)
                            applyMavenCentralRules.set(true)
                            stagingRepository(
                                project.layout.buildDirectory.dir(fixersConfig.publish.stagingDirectory.get()).get().asFile.absolutePath
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
     */
    private fun registerDeployTask(project: Project, fixersConfig: ConfigExtension) {
        project.tasks.register("deploy") {
            group = "publishing"
            description = "Publishes all artifacts using JReleaser"
            dependsOn("publish", "jreleaserDeploy")
        }

        registerDeployerTask(project, fixersConfig, "stage", PkgDeployType.STAGE)
        registerDeployerTask(project, fixersConfig, "promote", PkgDeployType.PROMOTE)
    }

    private fun registerDeployerTask(
        project: Project,
        fixersConfig: ConfigExtension,
        name: String,
        deployType: PkgDeployType
    ) {
        project.tasks.register(name) {
            group = "publishing"
            description = "Sets deployment type to ${deployType.name} and triggers the deploy task"
            doFirst {
                fixersConfig.publish.pkgDeployTypes.add(deployType)
            }
            dependsOn("publish")
            finalizedBy("deploy")
        }
    }
}
