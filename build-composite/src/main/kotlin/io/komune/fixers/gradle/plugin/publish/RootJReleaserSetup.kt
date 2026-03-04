package io.komune.fixers.gradle.plugin.publish

import io.komune.fixers.gradle.config.ConfigExtension
import io.komune.fixers.gradle.config.model.PkgDeployType
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jreleaser.gradle.plugin.JReleaserExtension
import org.jreleaser.gradle.plugin.dsl.deploy.maven.Maven
import org.jreleaser.gradle.plugin.dsl.deploy.maven.MavenDeployer
import org.jreleaser.model.Active

/**
 * Configures a single root-level JReleaser that collects staging directories
 * from all subprojects that have [PublishPlugin] applied.
 *
 * This reduces ~110 individual Maven Central ZIP uploads to 1 per module,
 * because JReleaser bundles all staging directories into a single deployment.
 */
object RootJReleaserSetup {

    fun configure(
        root: Project,
        fixersConfig: ConfigExtension,
        publishSubprojects: List<Project>
    ) {
        configureJReleaser(root, fixersConfig, publishSubprojects)
        registerTasks(root, fixersConfig, publishSubprojects)
    }

    private fun configureJReleaser(
        root: Project,
        fixersConfig: ConfigExtension,
        publishSubprojects: List<Project>
    ) {
        val versionFromFile = fixersConfig.bundle.version.get()
        if (root.version.toString().isEmpty()) {
            root.version = versionFromFile
        }

        root.extensions.configure<JReleaserExtension> {
            project {
                version.set(fixersConfig.bundle.version)
            }

            // Explicit GitHub release config to avoid JGit bug with submodule .git files.
            // We only deploy artifacts (skipRelease + skipTag), no actual GitHub release.
            release {
                github {
                    repoOwner.set(fixersConfig.bundle.url.map { JReleaserDeployer.parseRepoOwner(it) })
                    name.set(fixersConfig.bundle.url.map { JReleaserDeployer.parseRepoName(it) })
                    tagName.set("{{projectVersion}}")
                    skipRelease.set(true)
                    skipTag.set(true)
                }
            }

            gitRootSearch.set(true)

            deploy {
                maven {
                    mavenCentral(root, fixersConfig, publishSubprojects)
                    githubRepository(root, fixersConfig, publishSubprojects)
                    mavenCentralSnapshot(root, fixersConfig, publishSubprojects)
                }
            }
        }
    }

    private fun Maven.mavenCentral(
        root: Project,
        fixersConfig: ConfigExtension,
        publishSubprojects: List<Project>
    ) {
        mavenCentral {
            create("MAVENCENTRAL") {
                active.set(root.provider {
                    if (fixersConfig.publish.isPromote.get()) Active.RELEASE else Active.NEVER
                })
                sign.set(false)
                url.set(fixersConfig.publish.mavenCentralUrl)
                applyMavenCentralRules.set(true)
                snapshotSupported.set(false)
                stagingRepository(fixersConfig.publish.getStagingRepositoryPath(root))
                collectArtifactOverrides(publishSubprojects)
            }
        }
    }

    private fun Maven.githubRepository(
        root: Project,
        fixersConfig: ConfigExtension,
        publishSubprojects: List<Project>
    ) {
        github {
            create("GITHUB") {
                active.set(root.provider {
                    if (fixersConfig.publish.isStage.get()) Active.ALWAYS else Active.NEVER
                })
                sign.set(false)
                url.set(fixersConfig.publish.githubPackagesUrl)
                applyMavenCentralRules.set(false)
                snapshotSupported.set(true)
                stagingRepository(fixersConfig.publish.getStagingRepositoryPath(root))
                collectArtifactOverrides(publishSubprojects)
            }
        }
    }

    private fun Maven.mavenCentralSnapshot(
        root: Project,
        fixersConfig: ConfigExtension,
        publishSubprojects: List<Project>
    ) {
        nexus2 {
            create("SNAPSHOT") {
                active.set(root.provider {
                    if (fixersConfig.publish.isPromote.get()) Active.SNAPSHOT else Active.NEVER
                })
                sign.set(false)
                url.set(fixersConfig.publish.mavenSnapshotsUrl)
                snapshotUrl.set(fixersConfig.publish.mavenSnapshotsUrl)
                snapshotSupported.set(true)
                closeRepository.set(true)
                applyMavenCentralRules.set(false)
                stagingRepository(fixersConfig.publish.getStagingRepositoryPath(root))
                collectArtifactOverrides(publishSubprojects)
            }
        }
    }

    /**
     * Collects KMP non-JAR artifact overrides from all publish subprojects.
     * Workaround for https://github.com/jreleaser/jreleaser/issues/1784
     */
    @Suppress("MaxLineLength")
    private fun MavenDeployer.collectArtifactOverrides(publishSubprojects: List<Project>) {
        for (sub in publishSubprojects) {
            val kotlinExt = sub.extensions.findByType(KotlinMultiplatformExtension::class.java)
            kotlinExt?.targets?.forEach { target ->
                if (target !is KotlinJvmTarget) {
                    val nonJarArtifactId = if (target.platformType == KotlinPlatformType.wasm) {
                        "${sub.name}-wasm-${target.name.lowercase().substringAfter("wasm")}"
                    } else {
                        "${sub.name}-${target.name.lowercase()}"
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
    }

    private fun registerTasks(
        root: Project,
        fixersConfig: ConfigExtension,
        publishSubprojects: List<Project>
    ) {
        val allPublishTasks = publishSubprojects.map { "${it.path}:publish" }
        val pkgDeployTypes = fixersConfig.publish.pkgDeployTypes

        root.tasks.register("cleanStaging") {
            group = "publishing"
            description = "Cleans the staging directory before publishing"
            doLast {
                root.file(fixersConfig.publish.getStagingRepositoryPath(root)).deleteRecursively()
            }
        }

        publishSubprojects.forEach { sub ->
            sub.tasks.named("publish") {
                dependsOn(root.tasks.named("cleanStaging"))
            }
        }

        root.tasks.register("deploy") {
            group = "publishing"
            description = "Publishes all artifacts using JReleaser"
            dependsOn(allPublishTasks)
            dependsOn("jreleaserDeploy")
        }

        // Force jreleaserDeploy to never be UP-TO-DATE.
        // This is a deployment task with external side effects (pushes to remote repos).
        // Without this, Gradle caches the result between stage and promote runs in the same CI job,
        // causing promote to skip the actual deployment.
        // Also ensure it runs after all subproject publish tasks have populated the staging directory.
        root.tasks.named("jreleaserDeploy") {
            dependsOn(allPublishTasks)
            outputs.upToDateWhen { false }
        }

        registerDeployerTask(root, pkgDeployTypes, allPublishTasks, "stage", PkgDeployType.STAGE)
        registerDeployerTask(root, pkgDeployTypes, allPublishTasks, "promote", PkgDeployType.PROMOTE)
    }

    private fun registerDeployerTask(
        root: Project,
        pkgDeployTypes: ListProperty<PkgDeployType>,
        allPublishTasks: List<String>,
        name: String,
        deployType: PkgDeployType
    ) {
        root.tasks.register(name) {
            group = "publishing"
            description = "Sets deployment type to ${deployType.name} and triggers the deploy task"

            notCompatibleWithConfigurationCache("JReleaser tasks use Task.project at execution time")

            doFirst {
                pkgDeployTypes.add(deployType)
            }
            dependsOn(allPublishTasks)
            finalizedBy("deploy")
        }
    }
}
