package io.komune.fixers.gradle.plugin.kotlin

import java.io.File
import java.util.jar.Manifest as JarManifest
import org.gradle.api.java.archives.Manifest

/**
 * The `Implementation-Version` as the jar task writes it, lazy values resolved.
 */
internal fun Manifest.writtenVersion(): String? {
    val file = File.createTempFile("MANIFEST", ".MF")
    try {
        writeTo(file)
        return file.inputStream().use { JarManifest(it) }.mainAttributes.getValue("Implementation-Version")
    } finally {
        file.delete()
    }
}
