package com.only4.cap4k.ddd.core.domain.aggregate

/** Optional, convention-based aggregate-root lifecycle callbacks. */
interface AggregateLifecycleInvoker {
    fun onCreate(root: Any)

    fun onDeleted(root: Any)
}
