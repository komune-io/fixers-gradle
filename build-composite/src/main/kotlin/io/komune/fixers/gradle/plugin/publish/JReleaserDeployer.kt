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
import org.jreleaser.gradle.plugin.dsl.deploy.maven.Maven
import org.jreleaser.gradle.plugin.dsl.deploy.maven.MavenDeployer
import org.jreleaser.model.Active

/**
 * Configures JReleaser for publishing artifacts.
 */
@Suppress("TooManyFunctions")
object JReleaserDeployer {

    /**
     * Applies the JReleaser plugin to the project.
     * IMPORTANT: This must be called OUTSIDE of afterEvaluate to allow JReleaser's
     * internal afterEvaluate hooks to run and initialize fields like 'immutableRelease'.
     */
    fun applyPlugin(project: Project) {
        project.plugins.apply(JReleaserPlugin::class.java)
    }

    /**
     * Configures JReleaser extension for the given project.
     * This should be called in afterEvaluate after the plugin has been applied.
     */
    fun configure(project: Project, fixersConfig: ConfigExtension) {
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
    private fun configureJReleaser(
        project: Project,
        fixersConfig: ConfigExtension
    ) {
        project.extensions.configure<JReleaserExtension> {
            configureProjectSettings(this, fixersConfig)
//            configureSigningSettings(this)
            configureDeploymentSettings(this, project, fixersConfig)
            gitRootSearch.set(true)
        }
    }
    
    private fun configureProjectSettings(
        jReleaser: JReleaserExtension,
        fixersConfig: ConfigExtension
    ) {
        jReleaser.project {
            version.set(fixersConfig.bundle.version)
        }
    }
    
    private fun configureSigningSettings(jReleaser: JReleaserExtension) {
        jReleaser.signing {
            pgp {
                active.set(Active.ALWAYS)
                armored.set(true)
                verify.set(false)
            }
        }
    }
    
    private fun configureDeploymentSettings(
        jReleaser: JReleaserExtension,
        project: Project,
        fixersConfig: ConfigExtension
    ) {
        jReleaser.deploy {
            maven {
                mavenCentral(project, fixersConfig)
                githubRepository(project, fixersConfig)
                mavenCentralSnapShot(project, fixersConfig)
            }
        }
    }

    private fun Maven.mavenCentralSnapShot(
        project: Project,
        fixersConfig: ConfigExtension
    ) {

        nexus2 {
            create("SNAPSHOT") {
                // IMPORTANT: Use project.provider to defer the decision until execution time
                active.set(project.provider {
                    if (fixersConfig.publish.isPromote.get()) Active.SNAPSHOT else Active.NEVER
                })
                sign.set(false)
                url.set(fixersConfig.publish.mavenSnapshotsUrl)
                snapshotUrl.set(fixersConfig.publish.mavenSnapshotsUrl)
                snapshotSupported.set(true)
                closeRepository.set(true)
                applyMavenCentralRules.set(true)
                stagingRepository(
                    fixersConfig.publish.getStagingRepositoryPath(project)
                )
                workAroundJarFileNotFound(project)
            }
        }
    }

    private fun Maven.githubRepository(
        project: Project,
        fixersConfig: ConfigExtension
    ) {
        github {
            create("GITHUB") {
                // IMPORTANT: Use project.provider to defer the decision until execution time
                active.set(project.provider {
                    if (fixersConfig.publish.isStage.get()) Active.ALWAYS else Active.NEVER
                })
                sign.set(false)
                url.set(fixersConfig.publish.githubPackagesUrl)
                applyMavenCentralRules.set(true)
                snapshotSupported.set(true)
                stagingRepository(
                    fixersConfig.publish.getStagingRepositoryPath(project)
                )
                workAroundJarFileNotFound(project)
            }
        }
    }

    private fun Maven.mavenCentral(
        project: Project,
        fixersConfig: ConfigExtension
    ) {
        mavenCentral {
            create("MAVENCENTRAL") {
                // IMPORTANT: Use project.provider to defer the decision until execution time
                active.set(project.provider {
                    if (fixersConfig.publish.isPromote.get()) Active.RELEASE else Active.NEVER
                })
                sign.set(false)
                url.set(fixersConfig.publish.mavenCentralUrl)
                applyMavenCentralRules.set(true)
                snapshotSupported.set(false)
                stagingRepository(
                    fixersConfig.publish.getStagingRepositoryPath(project)
                )
                workAroundJarFileNotFound(project)
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
     * Registers the deployment task that depends on publication and jreleaserDeploy.
     */
    private fun registerDeployTask(project: Project, fixersConfig: ConfigExtension) {
        project.tasks.register("deploy") {
            group = "publishing"
            description = "Publishes all artifacts using JReleaser"
            dependsOn("publish", "jreleaserDeploy")
        }

        // Get the ListProperty reference - this is configuration cache safe
        val pkgDeployTypes = fixersConfig.publish.pkgDeployTypes

        registerDeployerTask(project, pkgDeployTypes, "stage", PkgDeployType.STAGE)
        registerDeployerTask(project, pkgDeployTypes, "promote", PkgDeployType.PROMOTE)
    }

    private fun registerDeployerTask(
        project: Project,
        pkgDeployTypes: org.gradle.api.provider.ListProperty<PkgDeployType>,
        name: String,
        deployType: PkgDeployType
    ) {
        project.tasks.register(name) {
            group = "publishing"
            description = "Sets deployment type to ${deployType.name} and triggers the deploy task"

            // Mark as not compatible with configuration cache
            // JReleaser itself isn't configuration cache compatible (uses Task.project at execution time)
            notCompatibleWithConfigurationCache("JReleaser tasks use Task.project at execution time")

            // Use the ListProperty directly
            doFirst {
                pkgDeployTypes.add(deployType)
            }
            dependsOn("publish")
            finalizedBy("deploy")
        }
    }
}
