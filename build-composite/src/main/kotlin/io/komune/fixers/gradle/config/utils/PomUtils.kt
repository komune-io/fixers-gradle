package io.komune.fixers.gradle.config.utils

import io.komune.fixers.gradle.config.model.Bundle
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPom


fun Project.pom(bundle: Bundle): Action<MavenPom> = Action {
    name.set(bundle.name)
    if (bundle.description.isPresent) description.set(bundle.description)
    if (bundle.url.isPresent) url.set(bundle.url)
    scm(bundle)
    licenses(bundle)
    developers(bundle)
}

private fun MavenPom.developers(bundle: Bundle) {
    developers {
        developer {
            if (bundle.developerId.isPresent) id.set(bundle.developerId)
            if (bundle.developerName.isPresent) name.set(bundle.developerName)
            if (bundle.developerOrganization.isPresent) organization.set(bundle.developerOrganization)
            if (bundle.developerOrganizationUrl.isPresent) organizationUrl.set(bundle.developerOrganizationUrl)
        }
    }
}

private fun MavenPom.licenses(bundle: Bundle) {
    licenses {
        license {
            if (bundle.licenseName.isPresent) name.set(bundle.licenseName)
            if (bundle.licenseUrl.isPresent) url.set(bundle.licenseUrl)
            if (bundle.licenseDistribution.isPresent) distribution.set(bundle.licenseDistribution)
        }
    }
}

private fun MavenPom.scm(bundle: Bundle) {
    scm {
        if (bundle.url.isPresent) url.set(bundle.url)
        if (bundle.scmConnection.isPresent) connection.set(bundle.scmConnection)
        if (bundle.scmDeveloperConnection.isPresent) developerConnection.set(bundle.scmDeveloperConnection)
    }
}
