import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.XmlProvider
import groovy.util.Node

// Provides POM configuration for Maven publications
project.extra["addPomConfiguration"] = { root: Node ->
    val licenses = root.appendNode("licenses")
    licenses.appendNode("license").apply {
        appendNode("name", "The Apache Software License, Version 2.0")
        appendNode("url", "https://www.apache.org/licenses/LICENSE-2.0.txt")
        appendNode("distribution", "repo")
    }

    val developers = root.appendNode("developers")
    developers.appendNode("developer").apply {
        appendNode("id", "Komune")
        appendNode("name", "Komune Team")
        appendNode("organization", "Komune")
        appendNode("organizationUrl", "https://komune.io")
    }

    val scm = root.appendNode("scm")
    scm.appendNode("url", "https://github.com/komune-io/fixers-gradle")
}

project.extra["configurePomMetadata"] = { xmlProvider: XmlProvider ->
    val root = xmlProvider.asNode()
    root.appendNode("url", "https://github.com/komune-io/fixers-gradle")
    val addPomConfiguration = project.extra["addPomConfiguration"] as (Node) -> Unit
    addPomConfiguration(root)
}

project.extra["configureMavenCentralMetadata"] = { xmlProvider: XmlProvider ->
    val root = xmlProvider.asNode()
    root.appendNode("name", project.name)
    root.appendNode("description", "Gradle plugin to facilitate kotlin multiplateform configuration.")
    root.appendNode("url", "https://github.com/komune-io/fixers-gradle")
    val addPomConfiguration = project.extra["addPomConfiguration"] as (Node) -> Unit
    addPomConfiguration(root)
}
