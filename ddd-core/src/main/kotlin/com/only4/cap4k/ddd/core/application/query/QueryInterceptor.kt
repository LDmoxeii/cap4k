package com.only4.cap4k.ddd.core.application.query

/**
 * Category-specific interception for one concrete [Query] type.
 */
interface QueryInterceptor<QUERY : Query<RESULT>, RESULT : Any> {
    fun beforeQuery(query: QUERY) = Unit

    fun afterQuery(query: QUERY, result: RESULT) = Unit
}
