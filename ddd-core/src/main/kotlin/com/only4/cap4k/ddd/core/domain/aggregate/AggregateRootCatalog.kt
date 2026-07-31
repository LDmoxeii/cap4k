package com.only4.cap4k.ddd.core.domain.aggregate

/** Runtime catalog of aggregate-root types owned by the active application model. */
fun interface AggregateRootCatalog {
    fun isAggregateRoot(entityType: Class<*>): Boolean
}
