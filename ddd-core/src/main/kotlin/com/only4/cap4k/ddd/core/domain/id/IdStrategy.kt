package com.only4.cap4k.ddd.core.domain.id

import kotlin.reflect.KClass

interface IdStrategy {
    val name: String
    val kind: IdGenerationKind
    val outputType: KClass<*>
    val preassignable: Boolean
    fun isDefaultValue(value: Any?): Boolean
    fun next(): Any
}
