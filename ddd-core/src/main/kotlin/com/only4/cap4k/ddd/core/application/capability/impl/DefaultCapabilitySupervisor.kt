package com.only4.cap4k.ddd.core.application.capability.impl

import com.only4.cap4k.ddd.core.application.capability.CapabilityCall
import com.only4.cap4k.ddd.core.application.capability.CapabilityHandler
import com.only4.cap4k.ddd.core.application.capability.CapabilityInterceptor
import com.only4.cap4k.ddd.core.application.capability.CapabilitySupervisor
import com.only4.cap4k.ddd.core.application.impl.SynchronousApplicationDispatcher
import com.only4.cap4k.ddd.core.domain.event.impl.EventRuntimeContext
import jakarta.validation.Validator

open class DefaultCapabilitySupervisor(
    handlers: List<CapabilityHandler<*, *>>,
    interceptors: List<CapabilityInterceptor<*, *>>,
    validator: Validator?,
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

    override fun <CALL : CapabilityCall<RESULT>, RESULT : Any> call(request: CALL): RESULT =
        EventRuntimeContext.withCausalFrame("Capability:${request.javaClass.name}") {
            dispatcher.dispatch(request)
        }
}
