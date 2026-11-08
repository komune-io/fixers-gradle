import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension


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
    name = "gradle-sandbox"
    description = "Sanbox to test Kotlin Configuration"
    url = "https://github.com/komune-io/fixers-gradle/tree/main/sandbox"
  }
  kt2Ts {
    outputDirectory = "build/d2/"
    inputDirectory = "ef"
  }
}

// Kotlin/JS owns kotlin-js-store/yarn.lock: refresh it with `make -f infra/make/libs.mk js-lock-refresh`.
// mocha 11 (pinned by KGP 2.4) declares serialize-javascript ^6, vulnerable to
// GHSA alerts fixed only in 7.x. The `**/` scope is required: without it yarn ignores the
// path, and an unscoped resolution would also hit karma's tree.
// Remove once KGP ships mocha >= 12, which depends on serialize-javascript ^7.
plugins.withType<YarnPlugin> {
  the<YarnRootExtension>().resolution("**/mocha/serialize-javascript", "7.1.4")
}
