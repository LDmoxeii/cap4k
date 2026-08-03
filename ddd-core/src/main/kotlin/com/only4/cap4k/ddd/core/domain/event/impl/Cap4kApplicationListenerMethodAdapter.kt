package com.only4.cap4k.ddd.core.domain.event.impl

import com.only4.cap4k.ddd.core.application.invocation.InvocationKind
import com.only4.cap4k.ddd.core.application.invocation.InvocationScopeManager
import org.springframework.context.ApplicationEvent
import org.springframework.context.event.ApplicationListenerMethodAdapter
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.UndeclaredThrowableException

open class Cap4kApplicationListenerMethodAdapter(
    private val descriptor: Cap4kEventHandlerDescriptor,
    private val invocationScopeManager: InvocationScopeManager,
) : ApplicationListenerMethodAdapter(descriptor.beanName, descriptor.targetClass, descriptor.method) {

    override fun supportsAsyncExecution(): Boolean = false

    override fun onApplicationEvent(event: ApplicationEvent) {
        val scope = EventRuntimeContext.currentOrNull()
        val previousMetadata = scope?.captureListenerMetadata()

        if (scope != null) {
            scope.listenerBeanName = descriptor.beanName
            scope.listenerClass = descriptor.targetClass
            scope.listenerMethod = descriptor.method
        }

        val invocationScope = invocationScopeManager.enter(InvocationKind.DOMAIN_EVENT_HANDLER)
        try {
            invocationScope.complete {
                EventRuntimeContext.withCausalFrame(
                    "Handler:${descriptor.targetClass.name}#${descriptor.method.name}",
                ) {
                    super.onApplicationEvent(event)
                }
            }
        } catch (ex: EventListenerInvocationException) {
            throw ex
        } catch (ex: Throwable) {
            val cause = unwrapInvocationCause(ex)
            throw EventListenerInvocationException(
                listenerBeanName = descriptor.beanName,
                listenerClass = descriptor.targetClass,
                listenerMethod = descriptor.method,
                eventPayloadClass = descriptor.eventPayloadClass,
                diagnosticContext = EventDispatchDiagnostics.snapshot(scope),
                cause = cause,
            )
        } finally {
            invocationScope.close()
            if (scope != null && previousMetadata != null) {
                scope.restoreListenerMetadata(previousMetadata)
            }
        }
    }

    override fun doInvoke(vararg args: Any?): Any? {
        val result = super.doInvoke(*args)
        if (result != null) {
            throw UnsupportedOperationException(
                "Cap4k event Handler ${descriptor.targetClass.name}#${descriptor.method.name} must return Unit/void",
            )
        }
        return null
    }

    private fun unwrapInvocationCause(ex: Throwable): Throwable =
        when (ex) {
            is InvocationTargetException -> ex.targetException ?: ex
            is UndeclaredThrowableException -> ex.undeclaredThrowable ?: ex
            else -> ex
        }
}
