plugins {
    `kotlin-dsl`
    id("composite.publishing-jreleaser")
}

apply(from = rootProject.file("gradle/publishing_module.gradle"))


project.plugins.withId("java-gradle-plugin") { // only do it if it's actually applied
    project.configure<GradlePluginDevelopmentExtension> {
        isAutomatedPublishing = false
    }
}
