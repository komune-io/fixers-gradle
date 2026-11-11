package io.komune.gradle.sandbox.domain.model

import kotlin.js.JsExport

typealias ObjectId = String
typealias ObjectName = String

@JsExport
interface ObjectModelDTO {
    val id: ObjectId
    val name: ObjectName
}

/** Strings only: its JSON and the Kotlin object differ by the marker and the data-class members alone. */
@JsExport
data class ObjectModel(
    override val id: ObjectId,
    override val name: ObjectName,
): ObjectModelDTO

/** A model built by Kotlin. */
@JsExport
fun sampleObjectModel(): ObjectModel = ObjectModel(id = "id-1", name = "a")
