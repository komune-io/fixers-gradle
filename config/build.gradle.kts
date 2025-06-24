plugins {
    `kotlin-dsl`
    id("composite.publishing-jreleaser")
    id("composite.publishing-module")
}

project.plugins.withId("java-gradle-plugin") { // only do it if it's actually applied
    project.configure<GradlePluginDevelopmentExtension> {
        isAutomatedPublishing = false
    }
}
