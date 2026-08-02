package io.komune.fixers.gradle.plugin.kotlin

import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.kotlin.dsl.extra
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for the Target enum helpers.
 */
class TargetTest {

    private fun projectWithTarget(target: String? = null): Project {
        val project = ProjectBuilder.builder().build()
        if (target != null) {
            project.extra.set("target", target)
        }
        return project
    }

    @Nested
    inner class CurrentTargetTest {

        @Test
        fun `should default to ALL when no target property is set`() {
            assertThat(Target.currentTarget(projectWithTarget())).isEqualTo(Target.ALL)
        }

        @Test
        fun `should resolve target property case-insensitively`() {
            assertThat(Target.currentTarget(projectWithTarget("jvm"))).isEqualTo(Target.JVM)
            assertThat(Target.currentTarget(projectWithTarget("JS"))).isEqualTo(Target.JS)
            assertThat(Target.currentTarget(projectWithTarget("Meta"))).isEqualTo(Target.META)
        }

        @Test
        fun `should fall back to ALL for unknown target values`() {
            assertThat(Target.currentTarget(projectWithTarget("wasm"))).isEqualTo(Target.ALL)
        }
    }

    @Nested
    inner class ShouldDefineTargetTest {

        @Test
        fun `ALL should define every target`() {
            val project = projectWithTarget("all")
            assertThat(Target.shouldDefineTarget(project, Target.JVM)).isTrue()
            assertThat(Target.shouldDefineTarget(project, Target.JS)).isTrue()
            assertThat(Target.shouldDefineTarget(project, Target.META)).isTrue()
        }

        @Test
        fun `JVM should define only JVM`() {
            val project = projectWithTarget("jvm")
            assertThat(Target.shouldDefineTarget(project, Target.JVM)).isTrue()
            assertThat(Target.shouldDefineTarget(project, Target.JS)).isFalse()
        }

        @Test
        fun `META should define every target`() {
            val project = projectWithTarget("meta")
            assertThat(Target.shouldDefineTarget(project, Target.JVM)).isTrue()
            assertThat(Target.shouldDefineTarget(project, Target.JS)).isTrue()
        }
    }

    @Nested
    inner class ShouldPublishTargetTest {

        @Test
        fun `META should publish only META`() {
            val project = projectWithTarget("meta")
            assertThat(Target.shouldPublishTarget(project, Target.META)).isTrue()
            assertThat(Target.shouldPublishTarget(project, Target.JVM)).isFalse()
            assertThat(Target.shouldPublishTarget(project, Target.JS)).isFalse()
        }

        @Test
        fun `ALL should publish every target`() {
            val project = projectWithTarget("all")
            assertThat(Target.shouldPublishTarget(project, Target.JVM)).isTrue()
            assertThat(Target.shouldPublishTarget(project, Target.JS)).isTrue()
        }

        @Test
        fun `JVM should publish only JVM`() {
            val project = projectWithTarget("jvm")
            assertThat(Target.shouldPublishTarget(project, Target.JVM)).isTrue()
            assertThat(Target.shouldPublishTarget(project, Target.JS)).isFalse()
        }
    }
}
