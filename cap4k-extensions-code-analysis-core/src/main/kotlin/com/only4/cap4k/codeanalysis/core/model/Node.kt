package com.only4.cap4k.codeanalysis.core.model

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
    clisendermethod,
    validator,
    command,
    commandhandler,
    query,
    queryhandler,
    cli,
    clihandler,
    aggregate,
    entitymethod,
    domainevent,
    domaineventhandler,
    integrationevent,
    integrationeventhandler,
    integrationeventconverter
}
