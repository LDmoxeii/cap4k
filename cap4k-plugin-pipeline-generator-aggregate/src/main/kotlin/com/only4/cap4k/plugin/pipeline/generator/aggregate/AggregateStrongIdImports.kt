package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.StrongIdModel

internal fun aggregateStrongIdImports(model: CanonicalModel, types: Iterable<String>): List<String> {
    val requestedTypes = types.map { it.removeSuffix("?") }.toSet()
    return model.strongIds
        .filter { strongId -> strongId.typeName in requestedTypes || strongId.fqn() in requestedTypes }
        .map { it.fqn() }
        .distinct()
}

private fun StrongIdModel.fqn(): String = "${packageName}.${typeName}"
