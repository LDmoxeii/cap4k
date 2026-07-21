package com.only4.cap4k.ddd.core.domain.id

import kotlin.reflect.KClass

/**
 * Compatibility allocation strategy used by manual application-side ID runtime support.
 * Generated Strong ID aggregate roots allocate through their generated ID type factories.
 */
interface IdStrategy {
    val name: String
    val kind: IdGenerationKind
    val outputType: KClass<*>
    val preassignable: Boolean
    fun isDefaultValue(value: Any?): Boolean
    fun next(): Any
}
