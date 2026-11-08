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
// The pinned versions, and why each one is needed, live in gradle/libs.versions.toml (`npm-*`).
// The `**/` scope is required: without it yarn ignores the path, and an unscoped resolution would
// also hit karma's own tree.
plugins.withType<YarnPlugin> {
  the<YarnRootExtension>().apply {
    resolution("**/mocha/serialize-javascript", libs.versions.npm.mocha.serialize.javascript.get())
    resolution("**/mocha/glob", libs.versions.npm.mocha.glob.get())
    resolution("**/mocha/minimatch", libs.versions.npm.mocha.minimatch.get())
    resolution("**/mocha/diff", libs.versions.npm.mocha.diff.get())
    resolution("**/karma-webpack/minimatch", libs.versions.npm.karma.webpack.minimatch.get())
  }
}
