package com.only4.cap4k.ddd.core.archinfo.model

/**
 * 元素
 *
 * @author LD_moxeii
 * @date 2025/07/27
 */
interface Element {
    companion object {
        const val TYPE_REF = "ref"
        const val TYPE_NONE = "none"
        const val TYPE_CATALOG = "catalog"
        const val TYPE_REPOSITORY = "repository"
        const val TYPE_FACTORY = "factory"
        const val TYPE_ENTITY = "entity"
        const val TYPE_VALUE_OBJECT = "value-object"
        const val TYPE_ENUM = "enum"
        const val TYPE_SPECIFICATION = "specification"
        const val TYPE_DOMAIN_SERVICE = "domain-service"
        const val TYPE_DOMAIN_EVENT = "domain-event"
        const val TYPE_INTEGRATION_EVENT = "integration-event"
        const val TYPE_SUBSCRIBER = "subscriber"
        const val TYPE_COMMAND = "command"
        const val TYPE_QUERY = "query"
        const val TYPE_SAGA = "saga"
        const val TYPE_REQUEST = "request"
    }

    val type: String
    val name: String
    val description: String
}
