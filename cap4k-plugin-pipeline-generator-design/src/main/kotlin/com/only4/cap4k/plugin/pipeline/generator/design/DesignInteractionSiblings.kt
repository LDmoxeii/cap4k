package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.CanonicalModel

internal fun CanonicalModel.designInteractionSiblingTypeNames(
    packageName: String,
    currentTypeName: String,
): Set<String> {
    return buildSet {
        commands.forEach { command ->
            addSiblingTypeName(command.packageName, command.typeName, packageName, currentTypeName)
        }
        queries.forEach { query ->
            addSiblingTypeName(query.packageName, query.typeName, packageName, currentTypeName)
        }
        clients.forEach { client ->
            addSiblingTypeName(client.packageName, client.typeName, packageName, currentTypeName)
        }
    }
}

private fun MutableSet<String>.addSiblingTypeName(
    candidatePackageName: String,
    candidateTypeName: String,
    packageName: String,
    currentTypeName: String,
) {
    if (candidatePackageName == packageName && candidateTypeName != currentTypeName) {
        add(candidateTypeName)
    }
}
