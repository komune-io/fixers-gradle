plugins {
    `kotlin-dsl`
}

apply(from = rootProject.file("gradle/publishing_module.gradle"))

project.plugins.withId("java-gradle-plugin") { // only do it if it's actually applied
    project.configure<GradlePluginDevelopmentExtension> {
        isAutomatedPublishing = false
    }
}
