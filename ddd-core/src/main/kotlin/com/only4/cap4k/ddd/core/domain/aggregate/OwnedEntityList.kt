package com.only4.cap4k.ddd.core.domain.aggregate

import kotlin.reflect.KClass

class OwnedEntityList<E : Any> internal constructor(
    private val delegate: MutableList<E>,
    private val entityType: KClass<E>,
    private val path: String,
) : List<E> by delegate {

    fun add(entity: E): Boolean = delegate.add(entity)

    fun remove(entity: E): Boolean = delegate.remove(entity)

    fun singleOrNull(): E? =
        when (delegate.size) {
            0 -> null
            1 -> delegate[0]
            else -> malformedSingleRelation()
        }

    fun replace(value: E?) {
        if (delegate.size > 1) {
            malformedSingleRelation()
        }
        delegate.clear()
        if (value != null) {
            add(value)
        }
    }

    private fun malformedSingleRelation(): Nothing =
        error("owned relation $path expected at most one ${entityType.simpleName} but found ${delegate.size}")

    companion object {
        fun <E : Any> of(
            delegate: MutableList<E>,
            entityType: KClass<E>,
            path: String,
        ): OwnedEntityList<E> =
            OwnedEntityList(delegate, entityType, path)
    }
}
