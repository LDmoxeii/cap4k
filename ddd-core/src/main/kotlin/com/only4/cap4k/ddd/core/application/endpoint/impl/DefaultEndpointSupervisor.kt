package com.only4.cap4k.ddd.core.application.endpoint.impl

import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.ddd.core.application.async.ApplicationAsyncExecutor
import com.only4.cap4k.ddd.core.application.async.failedStage
import com.only4.cap4k.ddd.core.application.context.ExecutionContextAccessor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextPropagation
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.ddd.core.application.endpoint.EndpointSupervisor
import com.only4.cap4k.ddd.core.application.impl.SynchronousApplicationDispatcher
import com.only4.cap4k.ddd.core.application.invocation.InvocationKind
import com.only4.cap4k.ddd.core.application.invocation.InvocationPolicy
import com.only4.cap4k.ddd.core.application.invocation.InvocationScopeManager
import com.only4.cap4k.ddd.core.domain.event.impl.EventRuntimeContext
import jakarta.validation.Validator
import java.util.concurrent.CompletionStage

open class DefaultEndpointSupervisor(
    handlers: List<EndpointHandler<*, *>>,
    validator: Validator?,
    private val invocationPolicy: InvocationPolicy,
    private val invocationScopeManager: InvocationScopeManager,
    private val executionContextAccessor: ExecutionContextAccessor,
    private val executionContextPropagation: ExecutionContextPropagation,
    private val asyncExecutor: ApplicationAsyncExecutor,
) : EndpointSupervisor {
    private val dispatcher = SynchronousApplicationDispatcher(
        category = "endpoint",
        handlers = handlers,
        handlerContract = EndpointHandler::class.java,
        interceptors = emptyList<Any>(),
        interceptorContract = EndpointHandler::class.java,
        validator = validator,
        invokeHandler = { handler, message ->
            @Suppress("UNCHECKED_CAST")
            (handler as EndpointHandler<EndpointRequest<Any>, Any>).handle(message as EndpointRequest<Any>)
        },
        beforeInvocation = { _, _ -> },
        afterInvocation = { _, _, _ -> },
    )

    fun init() = dispatcher.init()

    override fun <REQUEST : EndpointRequest<RESPONSE>, RESPONSE : Any> send(request: REQUEST): RESPONSE {
        invocationPolicy.check(InvocationKind.ENDPOINT)
        return invoke(request)
    }

    override fun <REQUEST : EndpointRequest<RESPONSE>, RESPONSE : Any> sendAsync(request: REQUEST): CompletionStage<RESPONSE> {
        val stage: CompletionStage<RESPONSE> = try {
            val snapshot = executionContextAccessor.current()
            invocationPolicy.check(InvocationKind.ENDPOINT, asynchronous = true)
            asyncExecutor.submit {
                EventRuntimeContext.withIsolatedState {
                    executionContextPropagation.withSnapshot(snapshot) { invoke(request) }
                }
            }
        } catch (ex: Throwable) {
            failedStage(ex)
        }
        return invocationScopeManager.track(stage)
    }

    private fun <REQUEST : EndpointRequest<RESPONSE>, RESPONSE : Any> invoke(request: REQUEST): RESPONSE {
        val scope = invocationScopeManager.enter(InvocationKind.ENDPOINT)
        return try {
            EventRuntimeContext.withCausalFrame("Endpoint:${request.javaClass.name}") {
                scope.complete { dispatcher.dispatch(request) }
            }
        } finally {
            scope.close()
        }
    }
}
