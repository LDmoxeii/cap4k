package com.only4.cap4k.ddd.core.application.query

/**
 * Internal runtime boundary for one Handler-wide read-only Query transaction.
 *
 * The persistence runtime owns the implementation and must reuse an active
 * Query execution for synchronous nested queries.
 */
interface QueryExecution {
    val active: Boolean

    fun <RESULT> execute(block: () -> RESULT): RESULT
}
