package io.komune.fixers.gradle.config.utils

import io.komune.fixers.gradle.config.ConfigExtension
import io.komune.fixers.gradle.config.model.Bundle
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.publish.maven.MavenPom

/**
 * Creates a POM configuration action using the provided bundle.
 * Falls back to root project's bundle configuration for any missing values,
 * since root project config may not be merged to subprojects yet when this runs
 * (afterEvaluate runs before projectsEvaluated where config merge happens).
 */
fun Project.pom(bundle: Bundle): Action<MavenPom> = Action {
    // Get root project's bundle as fallback (may have values not yet merged to subproject)
    val rootBundle = rootProject.extensions.findByType(ConfigExtension::class.java)?.bundle

    name.set(bundle.name.orFallback(rootBundle?.name))
    description.set(bundle.description.orFallback(rootBundle?.description))
    url.set(bundle.url.orFallback(rootBundle?.url))
    scm(bundle, rootBundle)
    licenses(bundle, rootBundle)
    developers(bundle, rootBundle)
}

/**
 * Returns this property's value if present, otherwise falls back to the other property's value.
 */
private fun <T : Any> Property<T>.orFallback(other: Property<T>?): T? {
    return if (this.isPresent) this.get() else other?.orNull
}

private fun MavenPom.developers(bundle: Bundle, rootBundle: Bundle?) {
    developers {
        developer {
            bundle.developerId.orFallback(rootBundle?.developerId)?.let { id.set(it) }
            bundle.developerName.orFallback(rootBundle?.developerName)?.let { name.set(it) }
            bundle.developerOrganization.orFallback(rootBundle?.developerOrganization)
                ?.let { organization.set(it) }
            bundle.developerOrganizationUrl.orFallback(rootBundle?.developerOrganizationUrl)
                ?.let { organizationUrl.set(it) }
        }
    }
}

private fun MavenPom.licenses(bundle: Bundle, rootBundle: Bundle?) {
    licenses {
        license {
            bundle.licenseName.orFallback(rootBundle?.licenseName)?.let { name.set(it) }
            bundle.licenseUrl.orFallback(rootBundle?.licenseUrl)?.let { url.set(it) }
            bundle.licenseDistribution.orFallback(rootBundle?.licenseDistribution)?.let { distribution.set(it) }
        }
    }
}

private fun MavenPom.scm(bundle: Bundle, rootBundle: Bundle?) {
    scm {
        bundle.url.orFallback(rootBundle?.url)?.let { url.set(it) }
        bundle.scmConnection.orFallback(rootBundle?.scmConnection)?.let { connection.set(it) }
        bundle.scmDeveloperConnection.orFallback(rootBundle?.scmDeveloperConnection)
            ?.let { developerConnection.set(it) }
    }
}
