import io.komune.fixers.gradle.config.ConfigPlugin
import io.komune.fixers.gradle.config.config

apply<ConfigPlugin>()

config {
    // License information
    licenseName.set("The Apache Software License, Version 2.0")
    licenseUrl.set("https://www.apache.org/licenses/LICENSE-2.0.txt")

    // Organization information
    organizationId.set("Komune")
    organizationName.set("Komune Team")
    organizationUrl.set("https://komune.io")

    // GitHub information
    githubOrganization.set("komune-io")
    githubProject.set("fixers-gradle")
}
