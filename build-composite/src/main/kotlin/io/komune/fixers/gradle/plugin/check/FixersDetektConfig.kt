package io.komune.fixers.gradle.plugin.check

/**
 * The Detekt configuration of every fixers build, on top of Detekt's defaults with every rule
 * active. Each entry below is an exemption decided once for all of them, with its reason.
 */
internal fun fixersDetektConfig(): String = """
    config:
      validation: true
""".trimIndent() + "\n"
