package io.komune.fixers.gradle.plugin.publish

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlatformPlugin

object PublishPlatformSetup {

    fun setupPlatformPublish(project: Project) {
        project.plugins.withType(JavaPlatformPlugin::class.java) {
            project.createMavenPublicationIfAbsent("javaPlatform")
        }
    }
}
