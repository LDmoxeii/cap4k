package com.only4.cap4k.ddd.core.domain.event.impl

import org.springframework.context.ApplicationListener
import org.springframework.context.event.EventListener
import org.springframework.context.event.EventListenerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.scheduling.annotation.Async
import org.springframework.transaction.event.TransactionalEventListener
import java.lang.reflect.Method

class Cap4kEventListenerFactory : EventListenerFactory, Ordered {

    override fun supportsMethod(method: Method): Boolean {
        val supported = AnnotatedElementUtils.hasAnnotation(method, EventListener::class.java) &&
            !AnnotatedElementUtils.hasAnnotation(method, TransactionalEventListener::class.java)
        if (supported) {
            check(
                !AnnotatedElementUtils.hasAnnotation(method, Async::class.java) &&
                    !AnnotatedElementUtils.hasAnnotation(method.declaringClass, Async::class.java)
            ) {
                "Cap4k synchronous event listener ${method.declaringClass.name}#${method.name} " +
                    "cannot use @Async; enqueue a reliable Command or publish an Integration Event instead"
            }
        }
        return supported
    }

    override fun createApplicationListener(
        beanName: String,
        type: Class<*>,
        method: Method,
    ): ApplicationListener<*> = Cap4kApplicationListenerMethodAdapter(beanName, type, method)

    override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE - 1
}
