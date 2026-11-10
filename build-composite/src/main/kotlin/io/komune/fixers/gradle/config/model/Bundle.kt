package io.komune.fixers.gradle.config.model

import io.komune.fixers.gradle.config.utils.mergeIfNotPresent
import io.komune.fixers.gradle.config.utils.orNullText
import io.komune.fixers.gradle.config.utils.property
import io.komune.fixers.gradle.config.utils.resolveVersion
import org.gradle.api.Project
import org.gradle.api.provider.Property

/**
 * Configuration for project bundle information.
 */
class Bundle(
    project: Project,
    name: String
) {
    /**
     * The name of the project.
     */
    val name: Property<String> = project.property(
        envKey = "FIXERS_BUNDLE_NAME",
        projectKey = "fixers.bundle.name",
        defaultValue = name
    )

    /**
     * The group (Maven groupId) of the project.
     */
    val group: Property<String> = project.property(
        envKey = "FIXERS_BUNDLE_GROUP",
        projectKey = "fixers.bundle.group"
    )

    /**
     * The description of the project.
     */
    val description: Property<String> = project.property(
        envKey = "FIXERS_BUNDLE_DESCRIPTION",
        projectKey = "fixers.bundle.description"
    )

    /**
     * The version of the project.
     */
    val version: Property<String> = project.property(
        envKey = "FIXERS_BUNDLE_VERSION",
        projectKey = "fixers.bundle.version",
        defaultValue = project.resolveVersion() ?: project.version.toString()
    )

    /**
     * The URL of the project.
     */
    val url: Property<String> = project.property(
        envKey = "FIXERS_BUNDLE_URL",
        projectKey = "fixers.bundle.url"
    )

    // License properties
    /**
     * The name of the license.
     */
    val licenseName: Property<String> = project.property(
        envKey = "FIXERS_BUNDLE_LICENSE_NAME",
        projectKey = "fixers.bundle.license.name",
        defaultValue = "The Apache Software License, Version 2.0"
    )

    /**
     * The URL of the license.
     */
    val licenseUrl: Property<String> = project.property(
        envKey = "FIXERS_BUNDLE_LICENSE_URL",
        projectKey = "fixers.bundle.license.url",
        defaultValue = "https://www.apache.org/licenses/LICENSE-2.0.txt"
    )

    /**
     * The distribution type of the license.
     */
    val licenseDistribution: Property<String> = project.property(
        envKey = "FIXERS_BUNDLE_LICENSE_DISTRIBUTION",
        projectKey = "fixers.bundle.license.distribution",
        defaultValue = "repo"
    )

    // Developer properties
    /**
     * The ID of the developer.
     */
    val developerId: Property<String> = project.property(
        envKey = "FIXERS_BUNDLE_DEVELOPER_ID",
        projectKey = "fixers.bundle.developer.id",
        defaultValue = "Komune"
    )

    /**
     * The name of the developer.
     */
    val developerName: Property<String> = project.property(
        envKey = "FIXERS_BUNDLE_DEVELOPER_NAME",
        projectKey = "fixers.bundle.developer.name",
        defaultValue = "Komune Team"
    )

    /**
     * The organization of the developer.
     */
    val developerOrganization: Property<String> = project.property(
        envKey = "FIXERS_BUNDLE_DEVELOPER_ORGANIZATION",
        projectKey = "fixers.bundle.developer.organization",
        defaultValue = "Komune"
    )

    /**
     * The URL of the developer's organization.
     */
    val developerOrganizationUrl: Property<String> = project.property(
        envKey = "FIXERS_BUNDLE_DEVELOPER_ORGANIZATION_URL",
        projectKey = "fixers.bundle.developer.organizationUrl",
        defaultValue = "https://komune.io"
    )

    // SCM properties
    /**
     * The connection URL for SCM.
     */
    val scmConnection: Property<String> = project.property(
        envKey = "FIXERS_BUNDLE_SCM_CONNECTION",
        projectKey = "fixers.bundle.scm.connection",
        defaultValue = "scm:git:git://github.com/komune-io/${project.rootProject.name}.git"
    )

    /**
     * The developer connection URL for SCM.
     */
    val scmDeveloperConnection: Property<String> = project.property(
        envKey = "FIXERS_BUNDLE_SCM_DEVELOPER_CONNECTION",
        projectKey = "fixers.bundle.scm.developerConnection",
        defaultValue = "scm:git:ssh://github.com/komune-io/${project.rootProject.name}.git"
    )

    override fun toString(): String = """
        Bundle(
            name='${name.orNullText()}',
            group=${group.orNullText()},
            description=${description.orNullText()}, 
            version=${version.orNullText()}, 
            url=${url.orNullText()},
            licenseName=${licenseName.orNullText()}, 
            licenseUrl=${licenseUrl.orNullText()}, 
            licenseDistribution=${licenseDistribution.orNullText()}, 
            developerId=${developerId.orNullText()}, 
            developerName=${developerName.orNullText()}, 
            developerOrganization=${developerOrganization.orNullText()}, 
            developerOrganizationUrl=${developerOrganizationUrl.orNullText()}, 
            scmConnection=${scmConnection.orNullText()}, 
            scmDeveloperConnection=${scmDeveloperConnection.orNullText()}
        )
    """.trimIndent()

    /**
     * Merges properties from the source Bundle into this Bundle.
     * Properties are only merged if the target property is not present and the source property is present.
     *
     * @param source The source Bundle to merge from
     */
    fun mergeFrom(source: Bundle) {
        // Basic properties
        name.mergeIfNotPresent(source.name)
        group.mergeIfNotPresent(source.group)
        description.mergeIfNotPresent(source.description)
        url.mergeIfNotPresent(source.url)

        // License properties
        licenseName.mergeIfNotPresent(source.licenseName)
        licenseUrl.mergeIfNotPresent(source.licenseUrl)
        licenseDistribution.mergeIfNotPresent(source.licenseDistribution)

        // Developer properties
        developerId.mergeIfNotPresent(source.developerId)
        developerName.mergeIfNotPresent(source.developerName)
        developerOrganization.mergeIfNotPresent(source.developerOrganization)
        developerOrganizationUrl.mergeIfNotPresent(source.developerOrganizationUrl)

        // SCM properties
        scmConnection.mergeIfNotPresent(source.scmConnection)
        scmDeveloperConnection.mergeIfNotPresent(source.scmDeveloperConnection)
    }
}
