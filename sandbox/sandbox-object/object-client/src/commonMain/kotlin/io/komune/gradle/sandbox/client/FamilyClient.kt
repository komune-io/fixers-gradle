package io.komune.gradle.sandbox.client

import io.komune.gradle.sandbox.domain.family.FamilyDTO
import kotlin.js.JsExport

// A second package whose declarations repeat the domain's: `Json<T>` of one applies to the other's types.

/** The tags of [family], counted by Kotlin code. */
@JsExport
fun countTags(family: FamilyDTO): Int = family.tags.size
