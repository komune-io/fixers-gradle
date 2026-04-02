package io.komune.fixers.gradle.config.model

import io.komune.fixers.gradle.config.utils.mergeIfNotPresent
import io.komune.fixers.gradle.config.utils.property
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.listProperty

class Repositories(
    private val project: Project
) {
    val mavenLocal: Property<Boolean> = project.property(
        envKey = "FIXERS_REPOSITORIES_MAVEN_LOCAL",
        projectKey = "fixers.repositories.mavenLocal",
        defaultValue = false
    )

    val mavenCentral: Property<Boolean> = project.property(
        envKey = "FIXERS_REPOSITORIES_MAVEN_CENTRAL",
        projectKey = "fixers.repositories.mavenCentral",
        defaultValue = true
    )

    val sonatypeSnapshots: Property<Boolean> = project.property(
        envKey = "FIXERS_REPOSITORIES_SONATYPE_SNAPSHOTS",
        projectKey = "fixers.repositories.sonatypeSnapshots",
        defaultValue = false
    )

    val mavenUrls: ListProperty<String> = project.objects.listProperty<String>()

    fun maven(url: String) {
        mavenUrls.add(url)
    }

    fun mergeFrom(source: Repositories): Repositories {
        mavenLocal.mergeIfNotPresent(source.mavenLocal)
        mavenCentral.mergeIfNotPresent(source.mavenCentral)
        sonatypeSnapshots.mergeIfNotPresent(source.sonatypeSnapshots)
        mavenUrls.mergeIfNotPresent(source.mavenUrls)
        return this
    }
}
