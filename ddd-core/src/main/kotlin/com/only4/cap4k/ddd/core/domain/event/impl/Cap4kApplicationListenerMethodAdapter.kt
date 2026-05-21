package com.only4.cap4k.ddd.core.domain.event.impl

import org.springframework.context.event.ApplicationListenerMethodAdapter
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.UndeclaredThrowableException

open class Cap4kApplicationListenerMethodAdapter(
    private val listenerBeanName: String,
    private val listenerClass: Class<*>,
    private val listenerMethod: Method,
) : ApplicationListenerMethodAdapter(listenerBeanName, listenerClass, listenerMethod) {

    override fun doInvoke(vararg args: Any?): Any? {
        val scope = EventRuntimeContext.currentOrNull()
        val previousMetadata = scope?.captureListenerMetadata()

        if (scope != null) {
            scope.listenerBeanName = listenerBeanName
            scope.listenerClass = listenerClass
            scope.listenerMethod = listenerMethod
        }

        try {
            val result = super.doInvoke(*args)
            if (result != null) {
                throw EventListenerInvocationException(
                    listenerBeanName = listenerBeanName,
                    listenerClass = listenerClass,
                    listenerMethod = listenerMethod,
                    eventPayloadClass = resolveEventPayloadClass(args),
                    diagnosticContext = EventDispatchException.snapshot(scope),
                    cause = UnsupportedOperationException(
                        "Event listener return-value publication is not supported by cap4k diagnostics adapter"
                    ),
                )
            }
            return null
        } catch (ex: EventListenerInvocationException) {
            throw ex
        } catch (ex: Throwable) {
            val cause = unwrapInvocationCause(ex)
            throw EventListenerInvocationException(
                listenerBeanName = listenerBeanName,
                listenerClass = listenerClass,
                listenerMethod = listenerMethod,
                eventPayloadClass = resolveEventPayloadClass(args),
                diagnosticContext = EventDispatchException.snapshot(scope),
                cause = cause,
            )
        } finally {
            if (scope != null && previousMetadata != null) {
                scope.restoreListenerMetadata(previousMetadata)
            }
        }
    }

    private fun resolveEventPayloadClass(args: Array<out Any?>): Class<*>? =
        args.firstOrNull { it != null }?.javaClass

    private fun unwrapInvocationCause(ex: Throwable): Throwable =
        when (ex) {
            is InvocationTargetException -> ex.targetException ?: ex
            is UndeclaredThrowableException -> ex.undeclaredThrowable ?: ex
            else -> ex
        }
}
