package io.komune.gradle.sandbox.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

// Runs through karma + webpack + mocha, so CI exercises kotlin-js-store/yarn.lock.
class ObjectModelTest {
    @Test
    fun copyKeepsIdAndReplacesName() {
        val model = ObjectModel(id = "id-1", name = "before")
        assertEquals(ObjectModel(id = "id-1", name = "after"), model.copy(name = "after"))
    }
}
