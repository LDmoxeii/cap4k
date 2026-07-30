package com.only4.cap4k.ddd.core.application.query.impl

import com.only4.cap4k.ddd.core.application.impl.SynchronousApplicationDispatcher
import com.only4.cap4k.ddd.core.application.query.Query
import com.only4.cap4k.ddd.core.application.query.QueryHandler
import com.only4.cap4k.ddd.core.application.query.QueryInterceptor
import com.only4.cap4k.ddd.core.application.query.QuerySupervisor
import com.only4.cap4k.ddd.core.domain.event.impl.EventRuntimeContext
import jakarta.validation.Validator

open class DefaultQuerySupervisor(
    handlers: List<QueryHandler<*, *>>,
    interceptors: List<QueryInterceptor<*, *>>,
    validator: Validator?,
) : QuerySupervisor {
    private val dispatcher = SynchronousApplicationDispatcher(
        category = "query",
        handlers = handlers,
        handlerContract = QueryHandler::class.java,
        interceptors = interceptors,
        interceptorContract = QueryInterceptor::class.java,
        validator = validator,
        invokeHandler = { handler, message ->
            @Suppress("UNCHECKED_CAST")
            (handler as QueryHandler<Query<Any>, Any>).handle(message as Query<Any>)
        },
        beforeInvocation = { interceptor, message ->
            @Suppress("UNCHECKED_CAST")
            (interceptor as QueryInterceptor<Query<Any>, Any>).beforeQuery(message as Query<Any>)
        },
        afterInvocation = { interceptor, message, result ->
            @Suppress("UNCHECKED_CAST")
            (interceptor as QueryInterceptor<Query<Any>, Any>).afterQuery(message as Query<Any>, result)
        },
    )

    fun init() = dispatcher.init()

    override fun <QUERY : Query<RESULT>, RESULT : Any> ask(query: QUERY): RESULT =
        EventRuntimeContext.withCausalFrame("Query:${query.javaClass.name}") {
            dispatcher.dispatch(query)
        }
}
