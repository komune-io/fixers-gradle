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
            configureReleaseSettings(this, fixersConfig)
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

    /**
     * Explicitly configures the GitHub release service so JReleaser does not need
     * to auto-detect it from git.
     *
     * Workaround for a JGit bug (fixed in JGit 7.0.0, but JReleaser 1.22.0 uses 5.13.3):
     * In git submodules, `.git` is a file (gitdir reference) instead of a directory.
     * JGit's `RepositoryCache.FileKey.resolve()` doesn't handle `.git` files, so it
     * falls back to using the working tree as the git directory. This causes
     * `loadConfig()` to resolve the git "config" file as `<repo>/config`, which
     * collides with the `config` Gradle subproject directory, throwing:
     *   FileNotFoundException: .../fixers-gradle/config (Is a directory)
     *
     * By explicitly providing the release service info, JReleaser skips git
     * auto-detection entirely. We only deploy artifacts (skipRelease + skipTag),
     * so no actual GitHub release is created.
     *
     * Can be removed once JReleaser upgrades to JGit >= 7.0.0.
     */
    private fun configureReleaseSettings(
        jReleaser: JReleaserExtension,
        fixersConfig: ConfigExtension
    ) {
        jReleaser.release {
            github {
                repoOwner.set(fixersConfig.bundle.url.map { parseRepoOwner(it) })
                name.set(fixersConfig.bundle.url.map { parseRepoName(it) })
                tagName.set("{{projectVersion}}")
                skipRelease.set(true)
                skipTag.set(true)
            }
        }
    }

    /**
     * Extracts the repository owner from a GitHub URL.
     * E.g. "https://github.com/komune-io/fixers-gradle" → "komune-io"
     */
    internal fun parseRepoOwner(url: String): String {
        val segments = url.trimEnd('/').removeSuffix(".git").split("/")
        return segments[segments.size - 2]
    }

    /**
     * Extracts the repository name from a GitHub URL.
     * E.g. "https://github.com/komune-io/fixers-gradle" → "fixers-gradle"
     */
    internal fun parseRepoName(url: String): String {
        return url.trimEnd('/').removeSuffix(".git").split("/").last()
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

        // Force jreleaserDeploy to never be UP-TO-DATE.
        // This is a deployment task with external side effects (pushes to remote repos).
        // Without this, Gradle caches the result between stage and promote runs in the same CI job,
        // causing promote to skip the actual deployment.
        project.tasks.named("jreleaserDeploy") {
            outputs.upToDateWhen { false }
        }

        // Serialize jreleaserDeploy tasks across subprojects to prevent a race condition
        // where parallel tasks download pomchecker to the same shared cache directory.
        // Only add ordering when this project sorts after the other (by path) to form a linear chain.
        project.rootProject.subprojects.forEach { otherProject ->
            if (otherProject.path < project.path) {
                otherProject.plugins.withType(JReleaserPlugin::class.java) {
                    project.tasks.named("jreleaserDeploy") {
                        mustRunAfter(otherProject.tasks.named("jreleaserDeploy"))
                    }
                }
            }
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
