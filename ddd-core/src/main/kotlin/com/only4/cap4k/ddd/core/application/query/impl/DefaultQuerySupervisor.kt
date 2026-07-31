package com.only4.cap4k.ddd.core.application.query.impl

import com.only4.cap4k.ddd.core.application.context.ExecutionContextAccessor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextPropagation
import com.only4.cap4k.ddd.core.application.impl.SynchronousApplicationDispatcher
import com.only4.cap4k.ddd.core.application.async.ApplicationAsyncExecutor
import com.only4.cap4k.ddd.core.application.async.failedStage
import com.only4.cap4k.ddd.core.application.invocation.InvocationKind
import com.only4.cap4k.ddd.core.application.invocation.InvocationPolicy
import com.only4.cap4k.ddd.core.application.invocation.InvocationScopeManager
import com.only4.cap4k.ddd.core.application.query.Query
import com.only4.cap4k.ddd.core.application.query.QueryHandler
import com.only4.cap4k.ddd.core.application.query.QueryInterceptor
import com.only4.cap4k.ddd.core.application.query.QueryExecution
import com.only4.cap4k.ddd.core.application.query.QuerySupervisor
import com.only4.cap4k.ddd.core.domain.event.impl.EventRuntimeContext
import jakarta.validation.Validator
import java.util.concurrent.CompletionStage

open class DefaultQuerySupervisor(
    handlers: List<QueryHandler<*, *>>,
    interceptors: List<QueryInterceptor<*, *>>,
    validator: Validator?,
    private val invocationPolicy: InvocationPolicy,
    private val invocationScopeManager: InvocationScopeManager,
    private val executionContextAccessor: ExecutionContextAccessor,
    private val executionContextPropagation: ExecutionContextPropagation,
    private val asyncExecutor: ApplicationAsyncExecutor,
    private val queryExecutionProvider: () -> QueryExecution,
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

    override fun <QUERY : Query<RESULT>, RESULT : Any> ask(query: QUERY): RESULT {
        invocationPolicy.check(InvocationKind.QUERY)
        return invoke(query)
    }

    override fun <QUERY : Query<RESULT>, RESULT : Any> askAsync(query: QUERY): CompletionStage<RESULT> {
        return try {
            val snapshot = executionContextAccessor.current()
            invocationPolicy.check(InvocationKind.QUERY, asynchronous = true)
            asyncExecutor.submit {
                EventRuntimeContext.withIsolatedState {
                    executionContextPropagation.withSnapshot(snapshot) {
                        invoke(query)
                    }
                }
            }
        } catch (ex: Throwable) {
            failedStage(ex)
        }
    }

    private fun <QUERY : Query<RESULT>, RESULT : Any> invoke(query: QUERY): RESULT {
        val scope = invocationScopeManager.enter(InvocationKind.QUERY)
        return try {
            EventRuntimeContext.withCausalFrame("Query:${query.javaClass.name}") {
                queryExecutionProvider().execute { dispatcher.dispatch(query) }
            }
        } finally {
            scope.close()
        }
    }
}
