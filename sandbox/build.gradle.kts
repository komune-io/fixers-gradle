
println(
  """
TOOL VERSIONS
  JDK: ${System.getProperty("java.version")}
  Gradle: ${gradle.gradleVersion}
""".trimIndent()
)

plugins {
  id("io.komune.fixers.gradle.config")
  id("io.komune.fixers.gradle.publish") apply false
  id("io.komune.fixers.gradle.check")
}

allprojects {
  group = "io.komune.gradle.sandbox"
  version = System.getenv("VERSION") ?: "latest"
  repositories {
    mavenLocal()
    mavenCentral()
  }
}

fixers {
  bundle {
    id = "gradle-sandbox"
    name = "gradle-sandbox"
    description = "Sanbox to test Kotlin Configuration"
    url = "https://github.com/komune-io/fixers-gradle/tree/main/sandbox"
  }
  kt2Ts {
    outputDirectory = "storybook/d2/"
    inputDirectory = "ef"
  }
}
