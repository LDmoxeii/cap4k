package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.CanonicalModel

internal fun CanonicalModel.designInteractionSiblingTypeNames(
    packageName: String,
    currentTypeName: String,
): Set<String> {
    return sequence {
        commands.forEach { command -> yield(command.packageName to command.typeName) }
        queries.forEach { query -> yield(query.packageName to query.typeName) }
        clients.forEach { client -> yield(client.packageName to client.typeName) }
    }
        .filter { (candidatePackageName, typeName) ->
            candidatePackageName == packageName && typeName != currentTypeName
        }
        .map { (_, typeName) -> typeName }
        .toSet()
}
