package com.only4.cap4k.plugin.codeanalysis.core.model

data class Node(
    val id: String,
    val name: String,
    val fullName: String,
    val type: NodeType
)

enum class NodeType {
    controller,
    controllermethod,
    commandsender,
    commandsendermethod,
    querysendermethod,
    capabilitysendermethod,
    validator,
    command,
    commandhandler,
    query,
    queryhandler,
    capability,
    capabilityhandler,
    aggregate,
    entitymethod,
    domainevent,
    domaineventhandler,
    integrationevent,
    integrationeventhandler,
    integrationeventconverter
}
