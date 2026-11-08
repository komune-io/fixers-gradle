package io.komune.fixers.gradle.plugin.config

import org.gradle.api.Project

/**
 * Configuration that the Kotlin Gradle plugin creates in every Kotlin project for its PGP
 * helper tasks (`generatePgpKeys`, `checkSigningConfiguration`). It is resolved in a worker
 * classpath only, never compiled against nor shipped, but it still lands in the dependency
 * graph that GitHub scans for security alerts.
 */
internal const val KOTLIN_BOUNCY_CASTLE_CONFIGURATION = "kotlinBouncyCastleConfiguration"

/**
 * BouncyCastle version forced on [KOTLIN_BOUNCY_CASTLE_CONFIGURATION].
 *
 * KGP hard-codes `bouncyCastleVersion=1.84` (2.4.21, 2.5.0-Beta2), which is affected by
 * GHSA-9pwp-9qqc-pr26 and GHSA-qp49-qgx5-5m26 (fixed in 1.85).
 * Remove this override (and [alignBouncyCastle]) once KGP's own `bouncyCastleVersion` is 1.85
 * or later: check `project.properties` in the kotlin-gradle-plugin jar.
 */
internal const val PATCHED_BOUNCY_CASTLE_VERSION = "1.86"

private const val BOUNCY_CASTLE_GROUP = "org.bouncycastle"

/**
 * Forces every `org.bouncycastle` module of [KOTLIN_BOUNCY_CASTLE_CONFIGURATION] to
 * [PATCHED_BOUNCY_CASTLE_VERSION]. No other configuration is touched, so a project that
 * declares BouncyCastle itself keeps the version it asked for.
 */
internal fun Project.alignBouncyCastle() {
    configurations.matching { it.name == KOTLIN_BOUNCY_CASTLE_CONFIGURATION }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == BOUNCY_CASTLE_GROUP) {
                useVersion(PATCHED_BOUNCY_CASTLE_VERSION)
                because("GHSA-9pwp-9qqc-pr26, GHSA-qp49-qgx5-5m26: KGP pins 1.84")
            }
        }
    }
}
