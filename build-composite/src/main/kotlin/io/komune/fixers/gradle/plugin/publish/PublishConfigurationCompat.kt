//package io.komune.fixers.gradle.plugin.publish
//
//import io.komune.fixers.gradle.config.ConfigExtension
//import io.komune.fixers.gradle.config.model.PublishConfig
//import org.gradle.api.Project
//
///**
// * Extension function to get a PublishConfiguration from a ConfigExtension.
// * This provides backward compatibility with the old PublishConfiguration class.
// *
// * This is a temporary solution to allow for a smooth transition from the old
// * PublishConfiguration class to the new PublishConfig class.
// */
//fun ConfigExtension.getPublishConfiguration(): PublishConfig {
//
//    publish.mavenCentralUrl.set(this.publish.mavenCentralUrl.get())
//    publish.mavenSnapshotsUrl.set(this.publish.mavenSnapshotsUrl.get())
//
//    val deployTypeStr = this.publish.pkgDeployType
//    publish.pkgDeployType.set(deployTypeStr)
//
//    val repoTypeStr = this.publish.pkgMavenRepo
//    publish.pkgMavenRepo.set(repoTypeStr)
//
//    publish.pkgGithubUsername.set(this.publish.pkgGithubUsername.get())
//    publish.pkgGithubToken.set(this.publish.pkgGithubToken.get())
//    publish.signingKey.set(this.publish.signingKey.get())
//    publish.signingPassword.set(this.publish.signingPassword.get())
//    publish.gradlePlugin.set(this.publish.gradlePlugin.get())
//
//    return publish
//}
