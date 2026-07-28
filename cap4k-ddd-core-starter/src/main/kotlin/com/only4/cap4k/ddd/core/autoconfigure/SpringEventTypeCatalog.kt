package com.only4.cap4k.ddd.core.autoconfigure

import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.event.EventTypeCatalog
import org.springframework.beans.factory.ListableBeanFactory
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.AnnotatedElementUtils

/** Uses Spring bean definitions and listener method signatures as the only event discovery root. */
class SpringEventTypeCatalog(
    private val beanFactory: ListableBeanFactory,
) : EventTypeCatalog {
    override fun integrationEventTypes(): Set<Class<*>> = buildSet {
        beanFactory.beanDefinitionNames.forEach { beanName ->
            val beanType = beanFactory.getType(beanName) ?: return@forEach
            (beanType.methods.asSequence() + beanType.declaredMethods.asSequence())
                .distinctBy { method -> method.toGenericString() }
                .filter { method -> AnnotatedElementUtils.hasAnnotation(method, EventListener::class.java) }
                .forEach { method ->
                    AnnotatedElementUtils.findMergedAnnotation(method, EventListener::class.java)
                        ?.classes
                        ?.map { it.java }
                        ?.filterTo(this) { it.isAnnotationPresent(IntegrationEvent::class.java) }
                    method.parameterTypes
                        .filterTo(this) { it.isAnnotationPresent(IntegrationEvent::class.java) }
                }
        }
    }
}
