package io.komune.fixers.gradle.plugin.publish

import org.gradle.api.Project

object PublishCatalogSetup {

    fun setupCatalogPublish(project: Project) {
        project.plugins.withId("version-catalog") {
            project.createMavenPublicationIfAbsent("versionCatalog")
        }
    }
}
