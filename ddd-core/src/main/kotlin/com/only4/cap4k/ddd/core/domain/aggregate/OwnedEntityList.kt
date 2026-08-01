package com.only4.cap4k.ddd.core.domain.aggregate

import com.only4.cap4k.ddd.core.domain.managed.ManagedEntityAdmissionCoordinatorSupport
import com.only4.cap4k.ddd.core.domain.managed.ManagedEntityAdmissionKind
import java.util.Collections
import kotlin.reflect.KClass

open class OwnedEntityList<E : Any> protected constructor(
    private val delegate: MutableList<E>,
    private val entityType: KClass<E>,
    private val path: String,
) : List<E> by Collections.unmodifiableList(delegate) {

    protected open fun prepareEntry(entity: E) = Unit

    fun add(entity: E): Boolean {
        ManagedEntityAdmissionCoordinatorSupport.admit(entity, ManagedEntityAdmissionKind.OWNED_CHILD)
        prepareEntry(entity)
        return delegate.add(entity)
    }

    fun remove(entity: E): Boolean = delegate.remove(entity)

    fun singleOrNull(): E? {
        check(delegate.size <= 1) {
            "owned relation $path expected at most one ${entityType.simpleName} but found ${delegate.size}"
        }
        return delegate.singleOrNull()
    }

    fun replace(value: E?) {
        check(delegate.size <= 1) {
            "owned relation $path expected at most one ${entityType.simpleName} but found ${delegate.size}"
        }
        if (value != null) {
            ManagedEntityAdmissionCoordinatorSupport.admit(value, ManagedEntityAdmissionKind.OWNED_CHILD)
            prepareEntry(value)
        }
        delegate.clear()
        if (value != null) delegate.add(value)
    }

    companion object {
        fun <E : Any> of(
            delegate: MutableList<E>,
            entityType: KClass<E>,
            path: String,
        ): OwnedEntityList<E> = object : OwnedEntityList<E>(delegate, entityType, path) {}

        fun <E : Any> of(
            delegate: MutableList<E>,
            entityType: KClass<E>,
            path: String,
            prepare: (E) -> Unit,
        ): OwnedEntityList<E> = object : OwnedEntityList<E>(delegate, entityType, path) {
            override fun prepareEntry(entity: E) = prepare(entity)
        }
    }
}
