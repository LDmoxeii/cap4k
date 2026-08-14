package com.only4.cap4k.plugin.codeanalysis.core.model

data class Node(
    val id: String,
    val name: String,
    val fullName: String,
    val type: NodeType,
    val missingMetadata: List<String> = emptyList(),
    val metadataOwner: String? = null,
)

enum class NodeType {
    controller,
    controllermethod,
    temporaltriggermethod,
    commandsender,
    querysendermethod,
    capabilitysendermethod,
    validator,
    command,
    commandhandler,
    query,
    queryhandler,
    capability,
    capabilityhandler,
    apipayload,
    domainservice,
    repository,
    aggregate,
    entitymethod,
    domainevent,
    domaineventhandler,
    integrationevent,
    integrationeventhandler,
    integrationeventconverter
}
