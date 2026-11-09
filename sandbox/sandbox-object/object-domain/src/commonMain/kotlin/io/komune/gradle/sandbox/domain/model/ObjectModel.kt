package io.komune.gradle.sandbox.domain.model

import kotlin.js.JsExport

typealias ObjectId = String
typealias ObjectName = String

@JsExport
interface ObjectModelDTO {
    val id: ObjectId
    val name: ObjectName
}

data class ObjectModel(
    override val id: ObjectId,
    override val name: ObjectName,
): ObjectModelDTO
