package io.komune.gradle.sandbox.domain.api

import io.komune.gradle.sandbox.domain.model.ObjectModel
import io.komune.gradle.sandbox.domain.model.ObjectName
import java.util.UUID

class ObjectService {

    fun create(objectName: ObjectName): ObjectModel {
        return ObjectModel(
            id = UUID.randomUUID().toString(),
            name = objectName
        )
    }
}
