package io.komune.fixers.gradle.plugin.npm.task

import io.komune.fixers.gradle.plugin.config.cleanProjectDir
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

open class NpmTsGenTask: DefaultTask() {

    @Input
    var buildDir: String = ""

    @Input
    var cleaning: Map<String, List<Pair<Regex, String>>> = emptyMap()

    @TaskAction
    fun doAction() {
        logger.info("[${name}] Run NpmTsGenTask...")
        logger.info("Cleaning: $cleaning")
        cleanProjectDir(buildDir, cleaning)
    }
}
