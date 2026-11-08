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
// KGP 2.4 pins mocha 11 and karma-webpack 5, whose declared ranges cap these transitives below
// their patched majors (GHSA alerts): mocha -> serialize-javascript ^6, glob ^10 (minimatch ^9),
// diff ^7; karma-webpack -> minimatch ^9. The `**/` scope is required: without it yarn ignores the
// path, and an unscoped resolution would also hit karma's own tree.
// Upstream bumps do not make a pin safe to drop (mocha 12 still asks for minimatch ^10, which is
// vulnerable): remove a pin only once `js-lock-refresh` without it still resolves patched versions
// for every advisory.
plugins.withType<YarnPlugin> {
  the<YarnRootExtension>().apply {
    resolution("**/mocha/serialize-javascript", "7.1.4")
    resolution("**/mocha/glob", "14.0.4")
    resolution("**/mocha/minimatch", "11.0.5")
    resolution("**/mocha/diff", "8.0.4")
    resolution("**/karma-webpack/minimatch", "11.0.5")
  }
}
