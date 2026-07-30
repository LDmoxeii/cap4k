package com.only4.cap4k.ddd.core.application.query

/**
 * Handles one concrete [Query] type in the current thread.
 */
fun interface QueryHandler<QUERY : Query<RESULT>, RESULT : Any> {
    fun handle(query: QUERY): RESULT
}
