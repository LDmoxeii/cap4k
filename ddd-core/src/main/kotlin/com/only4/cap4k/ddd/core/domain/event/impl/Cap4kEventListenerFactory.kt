package com.only4.cap4k.ddd.core.domain.event.impl

import org.springframework.context.ApplicationListener
import org.springframework.context.event.EventListenerFactory
import org.springframework.core.Ordered
import java.lang.reflect.Method

class Cap4kEventListenerFactory(
    private val descriptorResolver: Cap4kEventHandlerDescriptorResolver,
    private val registry: Cap4kEventHandlerRegistry,
    private val invocationScopeManager: com.only4.cap4k.ddd.core.application.invocation.InvocationScopeManager,
) : EventListenerFactory, Ordered {

    override fun supportsMethod(method: Method): Boolean =
        descriptorResolver.resolve("<discovery>", method.declaringClass, method) != null

    override fun createApplicationListener(
        beanName: String,
        type: Class<*>,
        method: Method,
    ): ApplicationListener<*> {
        val descriptor = requireNotNull(descriptorResolver.resolve(beanName, type, method)) {
            "Method ${type.name}#${method.name} is not a cap4k event Handler"
        }
        return Cap4kApplicationListenerMethodAdapter(descriptor, invocationScopeManager).also { listener ->
            registry.register(descriptor, listener)
        }
    }

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE
}
