package com.only4.cap4k.ddd.core.domain.aggregate.impl

import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateRootCatalog
import com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass

/**
 * Derives aggregate-root identity from the generated creation graph. Every
 * generated aggregate root has one AggregateFactory<Payload, Root>.
 */
class FactoryDerivedAggregateRootCatalog(
    factories: List<AggregateFactory<*, *>>,
) : AggregateRootCatalog {
    private val rootTypes = factories.mapTo(linkedSetOf()) { factory ->
        resolveGenericTypeClass(factory, 1, AggregateFactory::class.java).also { rootType ->
            check(rootType != Any::class.java) {
                "Cannot resolve aggregate-root type from factory ${factory.javaClass.name}"
            }
        }
    }

    override fun isAggregateRoot(entityType: Class<*>): Boolean =
        rootTypes.any { rootType -> rootType.isAssignableFrom(entityType) }
}
