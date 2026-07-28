package com.only4.cap4k.ddd.core.application.impl

import com.only4.cap4k.ddd.core.application.RequestHandler
import com.only4.cap4k.ddd.core.application.RequestInterceptor
import com.only4.cap4k.ddd.core.application.RequestParam
import com.only4.cap4k.ddd.core.application.RequestSupervisor
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.NoneResultCommandParam
import com.only4.cap4k.ddd.core.application.query.Query
import com.only4.cap4k.ddd.core.application.saga.SagaParam
import com.only4.cap4k.ddd.core.application.saga.SagaSupervisor
import com.only4.cap4k.ddd.core.domain.event.impl.EventDispatchException
import com.only4.cap4k.ddd.core.domain.event.impl.EventRuntimeContext
import com.only4.cap4k.ddd.core.domain.event.impl.EventRuntimeScopeType
import com.only4.cap4k.ddd.core.domain.event.impl.RequestDispatchException
import com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator

/**
 * Current-thread request dispatcher. It has no persisted request, scheduler or locker dependency.
 */
open class DefaultRequestSupervisor(
    private val requestHandlers: List<RequestHandler<*, *>>,
    private val requestInterceptors: List<RequestInterceptor<*, *>>,
    private val validator: Validator?,
) : RequestSupervisor {

    private val requestHandlerMap by lazy {
        requestHandlers.associateBy { handler ->
            resolveGenericTypeClass(
                handler,
                0,
                RequestHandler::class.java,
                Command::class.java,
                NoneResultCommandParam::class.java,
                Query::class.java,
            )
        }
    }

    private val requestInterceptorMap by lazy {
        requestInterceptors.groupBy { interceptor ->
            resolveGenericTypeClass(
                interceptor,
                0,
                RequestInterceptor::class.java,
                Command::class.java,
                NoneResultCommandParam::class.java,
                Query::class.java,
            )
        }
    }

    fun init() {
        requestHandlerMap
        requestInterceptorMap
    }

    override fun <REQUEST : RequestParam<RESPONSE>, RESPONSE : Any> send(request: REQUEST): RESPONSE {
        if (request is SagaParam<*>) {
            @Suppress("UNCHECKED_CAST")
            return SagaSupervisor.instance.send(request as SagaParam<RESPONSE>)
        }

        validate(request)
        return internalSend(request)
    }

    private fun validate(request: Any) {
        validator?.validate(request)?.takeIf { it.isNotEmpty() }?.let { violations ->
            throw ConstraintViolationException(violations)
        }
    }

    protected open fun <REQUEST : RequestParam<RESPONSE>, RESPONSE : Any> internalSend(request: REQUEST): RESPONSE {
        val requestClass = request::class.java
        val interceptors = requestInterceptorMap[requestClass].orEmpty()
        @Suppress("UNCHECKED_CAST")
        val handler = requestHandlerMap[requestClass] as? RequestHandler<REQUEST, RESPONSE>
            ?: throw RequestDispatchException(
                requestParamClass = requestClass,
                requestHandlerClass = null,
                diagnosticContext = EventDispatchException.snapshot(EventRuntimeContext.currentOrNull()),
                cause = IllegalStateException("No handler found for request type: ${requestClass.name}"),
            )

        val outerScope = EventRuntimeContext.currentOrNull()
        val requestScope = EventRuntimeContext.push(EventRuntimeScopeType.REQUEST)
        outerScope?.captureListenerMetadata()?.let(requestScope::restoreListenerMetadata)

        return try {
            interceptors.forEach { interceptor ->
                @Suppress("UNCHECKED_CAST")
                (interceptor as RequestInterceptor<REQUEST, RESPONSE>).preRequest(request)
            }

            val response = handler.exec(request)

            interceptors.forEach { interceptor ->
                @Suppress("UNCHECKED_CAST")
                (interceptor as RequestInterceptor<REQUEST, RESPONSE>).postRequest(request, response)
            }
            response
        } finally {
            EventRuntimeContext.restoreTo(outerScope)
        }
    }
}
