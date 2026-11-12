package io.komune.fixers.gradle.plugin.kotlin

import io.komune.fixers.gradle.config.fixers
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.provider.Provider
import org.gradle.testing.base.TestingExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

private val STRICT_ARGS = listOf("-Wextra", "-Xreport-all-warnings")
private const val RETURN_VALUE_CHECKER = "-Xreturn-value-checker="
private const val CHECK_RETURN_VALUES = "full"
private const val IGNORE_RETURN_VALUES = "disable"

private val SILENCING_ARGS = Regex(
    """-nowarn|-Xsuppress-warning=.*|-Xsuppress-version-warnings|-Xsuppress-deprecated-jvm-target-warning|""" +
        """-Xwarning-level=.*:(?!error${'$'}).*"""
)

/**
 * Every warning an error, the extra checkers on and every warning reported next to an error. The
 * extension holds `allWarningsAsErrors` final, and a compile task refuses to run with it off or with
 * an argument that silences a warning.
 *
 * Every compilation checks ignored return values (`-Xreturn-value-checker=full`), except the test
 * compilations of a build whose root sets `fixers.strictCompilation.returnValueCheckerInTests` to
 * false: they get `disable`, since `check` still reports the main functions `full` marks. A compile
 * task given another value refuses to run.
 */
internal fun Project.compileStrictly(options: KotlinCommonCompilerOptions) {
    options.allWarningsAsErrors.set(true)
    options.allWarningsAsErrors.disallowChanges()
    options.suppressWarnings.set(false)
    options.suppressWarnings.disallowChanges()
    options.freeCompilerArgs.addAll(STRICT_ARGS)
    checkReturnValues()
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

private fun Project.checkReturnValues() {
    val testsChecked = rootProject.extensions.fixers?.run { strictCompilation.returnValueCheckerInTests }
        ?: provider { true }
    // The default `test` suite is one of them; a multiplatform module has none.
    val testSuites = provider {
        extensions.findByType(TestingExtension::class.java)?.run { suites.withType(JvmTestSuite::class.java).names }
            .orEmpty()
    }
    kotlinCompilations { compilation ->
        val testCompilation = testSuites.map { suites ->
            compilation.name == KotlinCompilation.TEST_COMPILATION_NAME || compilation.name in suites
        }
        val mode: Provider<String> = testCompilation.zip(testsChecked) { test, checked ->
            if (test && !checked) IGNORE_RETURN_VALUES else CHECK_RETURN_VALUES
        }
        compilation.compileTaskProvider.configure {
            val args = compilerOptions.freeCompilerArgs
            args.add(mode.map { value -> RETURN_VALUE_CHECKER + value })
            val task = path
            doFirst {
                val expected = RETURN_VALUE_CHECKER + mode.get()
                val other = args.get().filter { arg -> arg.startsWith(RETURN_VALUE_CHECKER) && arg != expected }
                if (other.isNotEmpty()) {
                    throw GradleException(
                        "$task changes the return value checker fixers sets ($expected): " +
                            "remove ${other.joinToString()}."
                    )
                }
            }
        }
    }
}

private fun Project.kotlinCompilations(action: (KotlinCompilation<*>) -> Unit) {
    when (val kotlin = extensions.getByName("kotlin")) {
        is KotlinMultiplatformExtension -> kotlin.targets.configureEach { compilations.configureEach(action) }
        is KotlinSingleTargetExtension<*> -> kotlin.target.compilations.configureEach(action)
    }
}
