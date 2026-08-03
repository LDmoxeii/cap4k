package com.only4.cap4k.ddd.core.application.capability.impl

import com.only4.cap4k.ddd.core.application.capability.CapabilityCall
import com.only4.cap4k.ddd.core.application.capability.CapabilityHandler
import com.only4.cap4k.ddd.core.application.capability.CapabilityInterceptor
import com.only4.cap4k.ddd.core.application.capability.CapabilitySupervisor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextAccessor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextPropagation
import com.only4.cap4k.ddd.core.application.async.ApplicationAsyncExecutor
import com.only4.cap4k.ddd.core.application.impl.SynchronousApplicationDispatcher
import com.only4.cap4k.ddd.core.application.async.failedStage
import com.only4.cap4k.ddd.core.application.invocation.InvocationKind
import com.only4.cap4k.ddd.core.application.invocation.InvocationPolicy
import com.only4.cap4k.ddd.core.application.invocation.InvocationScopeManager
import com.only4.cap4k.ddd.core.domain.event.impl.EventRuntimeContext
import jakarta.validation.Validator
import java.util.concurrent.CompletionStage

open class DefaultCapabilitySupervisor(
    handlers: List<CapabilityHandler<*, *>>,
    interceptors: List<CapabilityInterceptor<*, *>>,
    validator: Validator?,
    private val invocationPolicy: InvocationPolicy,
    private val invocationScopeManager: InvocationScopeManager,
    private val executionContextAccessor: ExecutionContextAccessor,
    private val executionContextPropagation: ExecutionContextPropagation,
    private val asyncExecutor: ApplicationAsyncExecutor,
) : CapabilitySupervisor {
    private val dispatcher = SynchronousApplicationDispatcher(
        category = "capability",
        handlers = handlers,
        handlerContract = CapabilityHandler::class.java,
        interceptors = interceptors,
        interceptorContract = CapabilityInterceptor::class.java,
        validator = validator,
        invokeHandler = { handler, message ->
            @Suppress("UNCHECKED_CAST")
            (handler as CapabilityHandler<CapabilityCall<Any>, Any>).call(message as CapabilityCall<Any>)
        },
        beforeInvocation = { interceptor, message ->
            @Suppress("UNCHECKED_CAST")
            (interceptor as CapabilityInterceptor<CapabilityCall<Any>, Any>).beforeCall(
                message as CapabilityCall<Any>,
            )
        },
        afterInvocation = { interceptor, message, result ->
            @Suppress("UNCHECKED_CAST")
            (interceptor as CapabilityInterceptor<CapabilityCall<Any>, Any>).afterCall(
                message as CapabilityCall<Any>,
                result,
            )
        },
    )

    fun init() = dispatcher.init()

    override fun <CALL : CapabilityCall<RESULT>, RESULT : Any> call(request: CALL): RESULT {
        invocationPolicy.check(InvocationKind.CAPABILITY)
        return invoke(request)
    }

    override fun <CALL : CapabilityCall<RESULT>, RESULT : Any> callAsync(request: CALL): CompletionStage<RESULT> {
        val stage: CompletionStage<RESULT> = try {
            val snapshot = executionContextAccessor.current()
            invocationPolicy.check(InvocationKind.CAPABILITY, asynchronous = true)
            asyncExecutor.submit {
                EventRuntimeContext.withIsolatedState {
                    executionContextPropagation.withSnapshot(snapshot) {
                        invoke(request)
                    }
                }
            }
        } catch (ex: Throwable) {
            failedStage(ex)
        }
        return invocationScopeManager.track(stage)
    }

    private fun <CALL : CapabilityCall<RESULT>, RESULT : Any> invoke(request: CALL): RESULT {
        val scope = invocationScopeManager.enter(InvocationKind.CAPABILITY)
        return try {
            EventRuntimeContext.withCausalFrame("Capability:${request.javaClass.name}") {
                scope.complete { dispatcher.dispatch(request) }
            }
        } finally {
            scope.close()
        }
    }
}
