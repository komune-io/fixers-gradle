pluginManagement {
  // The fixers plugins come from the build above, the settings plugin included.
  includeBuild("../")
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins {
  id("io.komune.fixers.gradle.settings")
}

rootProject.name = "sandbox"

include(
  ":sandbox-object:object-domain",
  ":sandbox-object:object-api",
  ":sandbox-object:object-client",
)
