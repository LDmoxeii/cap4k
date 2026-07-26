package com.only4.cap4k.ddd.core.domain.id

import kotlin.reflect.KClass

interface GeneratedOwnIdAccessor<E : Any, ID : Any> {
    val entityType: KClass<E>
    val label: String

    fun current(entity: E): ID?
    fun assign(entity: E, id: ID)
    fun next(): ID

    fun assignIfMissing(entity: E): ID =
        GeneratedOwnId.assignIfMissing(
            current = { current(entity) },
            assign = { assign(entity, it) },
            next = ::next,
            label = label,
        )
}
