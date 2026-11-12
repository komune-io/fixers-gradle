package io.komune.fixers.gradle.plugin.publish

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Which staged files a snapshot sends, on a staging directory laid out as Gradle writes it
 * (timestamped names, `.module` urls in `-SNAPSHOT` form), modelled on fixers-f2's publish.
 */
class SnapshotFileSetTest {

    @TempDir
    lateinit var stagingDir: File

    private val ts = "0.47.0-20261112.084413-1"
    private val checksums = listOf("md5", "sha1", "sha256", "sha512")

    // A main file with its signature and four checksums, as a signed publish stages it.
    private fun stage(path: String, content: String = "bytes") {
        val file = File(stagingDir, path)
        file.parentFile.mkdirs()
        file.writeText(content)
        File(file.path + ".asc").writeText("signature")
        checksums.forEach { File(file.path + ".$it").writeText(it) }
    }

    private fun stageMetadata(path: String) {
        val file = File(stagingDir, path)
        file.parentFile.mkdirs()
        file.writeText("<metadata/>")
        checksums.forEach { File(file.path + ".$it").writeText(it) }
    }

    private fun moduleJson(vararg urls: String, availableAt: List<String> = emptyList()) = """
        {
          "formatVersion": "1.1",
          "variants": [
            ${urls.joinToString(",") { """{ "name": "v-$it", "files": [ { "name": "$it", "url": "$it" } ] }""" }}
            ${if (availableAt.isEmpty()) "" else ","}
            ${availableAt.joinToString(",") { """{ "name": "a-$it", "available-at": { "url": "../../$it/0.47.0-SNAPSHOT/$it-0.47.0-SNAPSHOT.module", "module": "$it" } }""" }}
          ]
        }
    """.trimIndent()

    private fun stageF2Like() {
        // KMP root: its .module names the metadata jar and the common sources; the targets are elsewhere.
        val root = "io/komune/f2/f2-client-core/0.47.0-SNAPSHOT"
        stage("$root/f2-client-core-$ts.pom")
        stage(
            "$root/f2-client-core-$ts.module",
            moduleJson(
                "f2-client-core-0.47.0-SNAPSHOT.jar",
                "f2-client-core-0.47.0-SNAPSHOT-sources.jar",
                availableAt = listOf("f2-client-core-jvm", "f2-client-core-js"),
            ),
        )
        stage("$root/f2-client-core-$ts.jar")
        stage("$root/f2-client-core-$ts-sources.jar")
        stage("$root/f2-client-core-$ts-javadoc.jar")
        stage("$root/f2-client-core-$ts-kotlin-tooling-metadata.json")
        stageMetadata("$root/maven-metadata.xml")
        stageMetadata("io/komune/f2/f2-client-core/maven-metadata.xml")
        // KMP targets: their .module names the jar or klib and the target sources.
        listOf("jvm" to "jar", "js" to "klib").forEach { (target, ext) ->
            val dir = "io/komune/f2/f2-client-core-$target/0.47.0-SNAPSHOT"
            stage("$dir/f2-client-core-$target-$ts.pom")
            stage(
                "$dir/f2-client-core-$target-$ts.module",
                moduleJson(
                    "f2-client-core-$target-0.47.0-SNAPSHOT.$ext",
                    "f2-client-core-$target-0.47.0-SNAPSHOT-sources.jar",
                ),
            )
            stage("$dir/f2-client-core-$target-$ts.$ext")
            stage("$dir/f2-client-core-$target-$ts-sources.jar")
            stage("$dir/f2-client-core-$target-$ts-javadoc.jar")
            stageMetadata("$dir/maven-metadata.xml")
        }
        // Plain JVM: its .module names the jar only; sources and javadoc are POM-side attachments.
        val jvm = "io/komune/f2/f2-spring-boot-starter-function/0.47.0-SNAPSHOT"
        stage("$jvm/f2-spring-boot-starter-function-$ts.pom")
        stage(
            "$jvm/f2-spring-boot-starter-function-$ts.module",
            moduleJson("f2-spring-boot-starter-function-0.47.0-SNAPSHOT.jar"),
        )
        stage("$jvm/f2-spring-boot-starter-function-$ts.jar")
        stage("$jvm/f2-spring-boot-starter-function-$ts-sources.jar")
        stage("$jvm/f2-spring-boot-starter-function-$ts-javadoc.jar")
        stageMetadata("$jvm/maven-metadata.xml")
        // Plugin marker: a pom and nothing else, no .module.
        val marker = "io/komune/fixers/f2/bom/io.komune.fixers.f2.bom.gradle.plugin/0.47.0-SNAPSHOT"
        stage("$marker/io.komune.fixers.f2.bom.gradle.plugin-$ts.pom")
        stageMetadata("$marker/maven-metadata.xml")
    }

    private fun staged() = stagingDir.walkTopDown().filter { it.isFile }.toList()

    private fun selected() = SnapshotFileSet.select(staged()).map { it.relativeTo(stagingDir).path }.toSet()

    @Test
    fun `every file a staged module names is selected`() {
        stageF2Like()

        val selected = selected()

        staged().filter { it.name.endsWith(".module") }.forEach { module ->
            val urls = """"url"\s*:\s*"([^"/]+)"""".toRegex().findAll(module.readText()).map { it.groupValues[1] }
            urls.forEach { url ->
                val suffix = url.substringAfter("0.47.0-SNAPSHOT")
                val dir = module.parentFile.relativeTo(stagingDir).path
                val artifactId = module.parentFile.parentFile.name
                assertThat(selected).describedAs("named by ${module.name}: $url").contains("$dir/$artifactId-$ts$suffix")
            }
        }
    }

    @Test
    fun `a snapshot sends sha1 only, no signatures`() {
        stageF2Like()

        val selected = selected()

        assertThat(selected).noneMatch { it.endsWith(".asc") }
        assertThat(selected).noneMatch { it.endsWith(".md5") || it.endsWith(".sha256") || it.endsWith(".sha512") }
        val mains = selected.filterNot { it.endsWith(".sha1") }
        assertThat(selected.filter { it.endsWith(".sha1") }).hasSameSizeAs(mains)
        mains.forEach { assertThat(selected).contains("$it.sha1") }
    }

    @Test
    fun `the attachments no module names are left out`() {
        stageF2Like()

        val selected = selected()

        assertThat(selected).noneMatch { it.contains("-javadoc.jar") }
        assertThat(selected).noneMatch { it.contains("kotlin-tooling-metadata.json") }
        assertThat(selected).noneMatch { it.contains("f2-spring-boot-starter-function-$ts-sources.jar") }
    }

    @Test
    fun `KMP sources, poms, modules, metadata and a pom-only marker are kept`() {
        stageF2Like()

        val selected = selected()

        assertThat(selected).contains(
            "io/komune/f2/f2-client-core/0.47.0-SNAPSHOT/f2-client-core-$ts-sources.jar",
            "io/komune/f2/f2-client-core-jvm/0.47.0-SNAPSHOT/f2-client-core-jvm-$ts-sources.jar",
            "io/komune/f2/f2-client-core-js/0.47.0-SNAPSHOT/f2-client-core-js-$ts.klib",
            "io/komune/f2/f2-spring-boot-starter-function/0.47.0-SNAPSHOT/f2-spring-boot-starter-function-$ts.pom",
            "io/komune/f2/f2-client-core/maven-metadata.xml",
            "io/komune/f2/f2-client-core/0.47.0-SNAPSHOT/maven-metadata.xml",
            "io/komune/fixers/f2/bom/io.komune.fixers.f2.bom.gradle.plugin/0.47.0-SNAPSHOT/" +
                "io.komune.fixers.f2.bom.gradle.plugin-$ts.pom",
        )
        // 4 modules × (pom, module, main file) + 3 KMP sources + 1 marker pom + 6 metadata, each with its sha1
        assertThat(selected).hasSize((4 * 3 + 3 + 1 + 6) * 2)
    }

    @Test
    fun `a name the rule cannot read is kept`() {
        val dir = "io/komune/x/lib/0.47.0-SNAPSHOT"
        stage("$dir/lib-$ts.pom")
        stage("$dir/lib-$ts.module", moduleJson("lib-0.47.0-SNAPSHOT.jar"))
        stage("$dir/lib-$ts.jar")
        stage("$dir/README.txt")

        assertThat(selected()).contains("$dir/README.txt", "$dir/README.txt.sha1")
    }
}
