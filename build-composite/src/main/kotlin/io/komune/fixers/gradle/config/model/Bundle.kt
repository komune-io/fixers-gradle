package io.komune.fixers.gradle.config.model

import io.komune.fixers.gradle.config.utils.mergeIfNotPresent
import io.komune.fixers.gradle.config.utils.property
import io.komune.fixers.gradle.config.utils.versionFromFile
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
     * The ID of the project.
     */
    val id: Property<String> = project.property(
        envKey = "FIXERS_BUNDLE_ID",
        projectKey = "fixers.bundle.id"
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
        defaultValue = project.versionFromFile() ?: project.version.toString()
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
        defaultValue = "scm:git:git://github.com/komune-io/fixers-gradle.git"
    )

    /**
     * The developer connection URL for SCM.
     */
    val scmDeveloperConnection: Property<String> = project.property(
        envKey = "FIXERS_BUNDLE_SCM_DEVELOPER_CONNECTION",
        projectKey = "fixers.bundle.scm.developerConnection",
        defaultValue = "scm:git:ssh://github.com/komune-io/fixers-gradle.git"
    )

    override fun toString(): String {
        return """
            Bundle(
                name='${name.orNull}',
                group=${group.orNull},
                id=${id.orNull},
                description=${description.orNull}, 
                version=${version.orNull}, 
                url=${url.orNull},
                licenseName=${licenseName.orNull}, 
                licenseUrl=${licenseUrl.orNull}, 
                licenseDistribution=${licenseDistribution.orNull}, 
                developerId=${developerId.orNull}, 
                developerName=${developerName.orNull}, 
                developerOrganization=${developerOrganization.orNull}, 
                developerOrganizationUrl=${developerOrganizationUrl.orNull}, 
                scmConnection=${scmConnection.orNull}, 
                scmDeveloperConnection=${scmDeveloperConnection.orNull}
            )
        """.trimIndent()
    }

    /**
     * Merges properties from the source Bundle into this Bundle.
     * Properties are only merged if the target property is not present and the source property is present.
     *
     * @param source The source Bundle to merge from
     * @return This Bundle after merging
     */
    fun mergeFrom(source: Bundle): Bundle {
        // Basic properties
        name.mergeIfNotPresent(source.name)
        group.mergeIfNotPresent(source.group)
        id.mergeIfNotPresent(source.id)
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

        return this
    }
}
