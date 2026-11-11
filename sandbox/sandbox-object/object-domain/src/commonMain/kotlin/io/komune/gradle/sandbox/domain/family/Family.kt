package io.komune.gradle.sandbox.domain.family

import kotlin.js.JsExport
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

// One declaration per family of types whose Kotlin object and JSON differ, so that the generated
// contract and its `Json<T>` are checked on the compiler's own output.

@JsExport
enum class Shape { ROUND, SQUARE }

@JsExport
enum class Level(val weight: Int) {
    LOW(weight = 1),
    HIGH(weight = 2);

    fun heavierThan(other: Level): Boolean = weight > other.weight
}

@JsExport
@OptIn(ExperimentalTime::class)
interface FamilyDTO {
    val tags: List<String>
    val owners: Set<String>
    val counts: Map<String, List<Long>>
    val total: Long
    val shape: Shape
    val level: Level

    // What the export cannot express reaches the declarations only where a build allows it.
    @Suppress("NON_EXPORTABLE_TYPE")
    val createdAt: Instant
    val note: String?
}

@JsExport
@OptIn(ExperimentalTime::class)
// One parameter per family the contract is checked on.
@Suppress("NON_EXPORTABLE_TYPE", "LongParameterList")
data class Family(
    override val tags: List<String>,
    override val owners: Set<String>,
    override val counts: Map<String, List<Long>>,
    override val total: Long,
    override val shape: Shape,
    override val level: Level,
    @Suppress("NON_EXPORTABLE_TYPE")
    override val createdAt: Instant,
    override val note: String?,
) : FamilyDTO {
    companion object {
        /** The name a family is filed under. */
        const val KIND: String = "family"
    }
}

/** Reads every family of [family], as Kotlin code does. */
@JsExport
fun describeFamily(family: FamilyDTO): String =
    "${family.tags.size} ${family.owners.size} ${family.counts.size} ${family.total + 1} " +
        "${family.shape.name} ${family.level.heavierThan(Level.LOW)} ${family.note?.length ?: 0}"

/** A family whose values are known, built by Kotlin. */
@JsExport
@OptIn(ExperimentalTime::class)
fun sampleFamily(): Family = Family(
    tags = listOf("a", "b"),
    owners = setOf("o"),
    counts = mapOf("x" to listOf(1L, 2L)),
    total = 9_007_199_254_740_993L,
    shape = Shape.ROUND,
    level = Level.HIGH,
    createdAt = Instant.fromEpochSeconds(epochSeconds = 0),
    note = null,
)
