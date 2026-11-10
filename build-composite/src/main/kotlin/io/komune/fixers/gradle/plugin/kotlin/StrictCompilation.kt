package io.komune.fixers.gradle.plugin.kotlin

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerOptions
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

private val STRICT_ARGS = listOf("-Wextra", "-Xreturn-value-checker=full", "-Xreport-all-warnings")

private val SILENCING_ARGS = Regex(
    """-nowarn|-Xsuppress-warning=.*|-Xsuppress-version-warnings|-Xsuppress-deprecated-jvm-target-warning|""" +
        """-Xwarning-level=.*:(?!error${'$'}).*"""
)

/**
 * Every warning an error, the extra checkers on and every warning reported next to an error. The
 * extension holds `allWarningsAsErrors` final, and a compile task refuses to run with it off or with
 * an argument that silences a warning.
 */
internal fun Project.compileStrictly(options: KotlinCommonCompilerOptions) {
    options.allWarningsAsErrors.set(true)
    options.allWarningsAsErrors.disallowChanges()
    options.suppressWarnings.set(false)
    options.suppressWarnings.disallowChanges()
    options.freeCompilerArgs.addAll(STRICT_ARGS)
    // The tasks take their options from the extension as conventions, which KGP sets again after this
    // action: they are checked when they run.
    tasks.withType(KotlinCompilationTask::class.java).configureEach {
        val warningsAsErrors = compilerOptions.allWarningsAsErrors
        val suppressed = compilerOptions.suppressWarnings
        val args = compilerOptions.freeCompilerArgs
        val task = path
        doFirst {
            val silencing = args.get().filter(SILENCING_ARGS::matches) +
                listOfNotNull(
                    "allWarningsAsErrors = false".takeUnless { warningsAsErrors.get() },
                    "suppressWarnings = true".takeIf { suppressed.get() },
                )
            if (silencing.isNotEmpty()) {
                throw GradleException(
                    "$task silences warnings, which fixers makes errors: remove ${silencing.joinToString()}."
                )
            }
        }
    }
}
