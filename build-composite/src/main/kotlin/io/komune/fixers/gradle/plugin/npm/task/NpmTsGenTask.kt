package io.komune.fixers.gradle.plugin.npm.task

import io.komune.fixers.gradle.config.fixers
import io.komune.fixers.gradle.plugin.config.buildCleaningRegex
import io.komune.fixers.gradle.plugin.config.cleanProject
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

open class NpmTsGenTask: DefaultTask() {
    @TaskAction
    fun doAction() {
        project.logger.info("[${project.name}]Run NpmTsGenTask...")
        project.rootProject.extensions.fixers?.kt2Ts?.let { config ->
            val cleaning = config.buildCleaningRegex()
            project.logger.info("Cleaning: $cleaning")
            project.cleanProject(cleaning)
        }

    }
}
