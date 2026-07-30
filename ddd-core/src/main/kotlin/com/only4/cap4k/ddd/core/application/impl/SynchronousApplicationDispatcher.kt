package com.only4.cap4k.ddd.core.application.impl

import com.only4.cap4k.ddd.core.domain.event.impl.EventRuntimeContext
import com.only4.cap4k.ddd.core.domain.event.impl.EventRuntimeScopeType
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import org.springframework.aop.support.AopUtils
import org.springframework.core.ResolvableType

/**
 * Shared synchronous invocation mechanics. Public application categories remain independent.
 */
internal class SynchronousApplicationDispatcher(
    private val category: String,
    handlers: List<*>,
    private val handlerContract: Class<*>,
    interceptors: List<*>,
    private val interceptorContract: Class<*>,
    private val validator: Validator?,
    private val invokeHandler: (handler: Any, message: Any) -> Any,
    private val beforeInvocation: (interceptor: Any, message: Any) -> Unit,
    private val afterInvocation: (interceptor: Any, message: Any, result: Any) -> Unit,
) {
    private val handlerComponents = handlers.filterNotNull()
    private val interceptorComponents = interceptors.filterNotNull()

    private val handlerMap by lazy {
        uniqueHandlersByMessageType()
    }

    private val interceptorMap by lazy {
        interceptorComponents.groupBy { interceptor ->
            resolveMessageType(interceptor, interceptorContract, "interceptor")
        }
    }

    fun init() {
        handlerMap
        interceptorMap
    }

    fun <MESSAGE : Any, RESULT : Any> dispatch(message: MESSAGE): RESULT {
        validate(message)
        val messageType = message.javaClass
        val handler = handlerMap[messageType]
            ?: error("No $category handler found for message type: ${messageType.name}")
        val interceptors = interceptorMap[messageType].orEmpty()

        val outerScope = EventRuntimeContext.currentOrNull()
        val invocationScope = EventRuntimeContext.push(EventRuntimeScopeType.APPLICATION_INVOCATION)
        outerScope?.captureListenerMetadata()?.let(invocationScope::restoreListenerMetadata)

        return try {
            interceptors.forEach { interceptor -> beforeInvocation(interceptor, message) }
            val result = invokeHandler(handler, message)
            interceptors.forEach { interceptor -> afterInvocation(interceptor, message, result) }
            @Suppress("UNCHECKED_CAST")
            result as RESULT
        } finally {
            EventRuntimeContext.restoreTo(outerScope)
        }
    }

    private fun uniqueHandlersByMessageType(): Map<Class<*>, Any> {
        val grouped = handlerComponents.groupBy { handler ->
            resolveMessageType(handler, handlerContract, "handler")
        }
        grouped.entries.firstOrNull { (_, components) -> components.size > 1 }?.let { (messageType, components) ->
            error(
                "Multiple $category handlers found for ${messageType.name}: " +
                    components.joinToString { AopUtils.getTargetClass(it).name },
            )
        }
        return grouped.mapValues { (_, components) -> components.single() }
    }

    private fun resolveMessageType(component: Any, contract: Class<*>, componentKind: String): Class<*> {
        val targetClass = AopUtils.getTargetClass(component)
        val resolved = ResolvableType.forClass(targetClass)
            .`as`(contract)
            .getGeneric(0)
            .resolve()
        require(resolved != null && resolved != Any::class.java) {
            "Cannot resolve $category $componentKind message type from ${targetClass.name}"
        }
        return resolved
    }

    private fun validate(message: Any) {
        validator?.validate(message)?.takeIf { it.isNotEmpty() }?.let { violations ->
            throw ConstraintViolationException(violations)
        }
    }
}
