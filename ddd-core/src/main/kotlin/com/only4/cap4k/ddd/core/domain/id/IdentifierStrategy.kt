package com.only4.cap4k.ddd.core.domain.id

import kotlin.reflect.KClass

interface IdentifierStrategy {
    val name: String
    val capabilities: Set<IdentifierCapability>

    fun supports(type: KClass<*>): Boolean
    fun <T : Any> next(type: KClass<T>): T
    fun isDefaultValue(value: Any?, type: KClass<*>): Boolean
}
