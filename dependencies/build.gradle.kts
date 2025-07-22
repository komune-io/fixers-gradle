plugins {
    `kotlin-dsl`
    id("io.komune.fixers.gradle.kotlin.jvm")
    id("io.komune.fixers.gradle.publishing")
}

project.plugins.withId("java-gradle-plugin") { // only do it if it's actually applied
    project.configure<GradlePluginDevelopmentExtension> {
        isAutomatedPublishing = false
    }
}
