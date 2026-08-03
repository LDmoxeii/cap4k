package com.only4.cap4k.ddd.core.autoconfigure

import com.only4.cap4k.ddd.core.domain.event.EventTypeCatalog
import com.only4.cap4k.ddd.core.domain.event.impl.Cap4kEventHandlerDescriptorResolver
import com.only4.cap4k.ddd.core.domain.event.impl.Cap4kEventKind
import org.springframework.beans.factory.ListableBeanFactory

/** Uses Spring bean definitions and listener method signatures as the only event discovery root. */
class SpringEventTypeCatalog(
    private val beanFactory: ListableBeanFactory,
    private val descriptorResolver: Cap4kEventHandlerDescriptorResolver,
) : EventTypeCatalog {
    override fun integrationEventTypes(): Set<Class<*>> = buildSet {
        beanFactory.beanDefinitionNames.forEach { beanName ->
            val beanType = beanFactory.getType(beanName) ?: return@forEach
            descriptorResolver.resolveMethods(beanName, beanType)
                .asSequence()
                .filter { descriptor -> descriptor.eventKind == Cap4kEventKind.INTEGRATION }
                .mapTo(this) { descriptor -> descriptor.eventPayloadClass }
        }
    }
}
