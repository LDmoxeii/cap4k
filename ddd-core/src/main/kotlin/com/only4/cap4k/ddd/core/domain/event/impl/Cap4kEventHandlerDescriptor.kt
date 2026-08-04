package com.only4.cap4k.ddd.core.domain.event.impl

import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent
import org.springframework.aop.support.AopUtils
import org.springframework.context.event.EventListener
import org.springframework.core.BridgeMethodResolver
import org.springframework.core.KotlinDetector
import org.springframework.core.MethodIntrospector
import org.springframework.core.Ordered
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.core.annotation.Order
import org.springframework.scheduling.annotation.Async
import org.springframework.transaction.event.TransactionalEventListener
import java.lang.reflect.Method
import java.lang.reflect.Modifier

enum class Cap4kEventKind {
    DOMAIN,
    INTEGRATION,
}

data class Cap4kEventHandlerDescriptor(
    val beanName: String,
    val targetClass: Class<*>,
    val method: Method,
    val eventPayloadClass: Class<*>,
    val eventKind: Cap4kEventKind,
    val condition: String,
    val order: Int,
)

/** The single resolver used by listener discovery, validation, dispatch registration, and transport catalogs. */
class Cap4kEventHandlerDescriptorResolver {
    fun resolve(
        beanName: String,
        targetClass: Class<*>,
        method: Method,
    ): Cap4kEventHandlerDescriptor? {
        val targetMethod = BridgeMethodResolver.findBridgedMethod(
            AopUtils.getMostSpecificMethod(method, targetClass),
        )
        val eventListener = AnnotatedElementUtils.findMergedAnnotation(targetMethod, EventListener::class.java)
            ?: AnnotatedElementUtils.findMergedAnnotation(method, EventListener::class.java)
            ?: return null
        val declaredEventClasses = eventListener.classes.map { it.java }
        val parameterClasses = targetMethod.parameterTypes.toList()
        val isCap4kCandidate = (declaredEventClasses + parameterClasses).any(::isCap4kEvent)
        if (!isCap4kCandidate) return null

        require(
            !AnnotatedElementUtils.hasAnnotation(targetMethod, TransactionalEventListener::class.java) &&
                !AnnotatedElementUtils.hasAnnotation(method, TransactionalEventListener::class.java)
        ) {
            invalid(
                targetClass,
                targetMethod,
                "@TransactionalEventListener is unsupported; send a Command from an ordinary synchronous @EventListener",
            )
        }
        require(eventListener.defaultExecution) {
            invalid(targetClass, targetMethod, "defaultExecution=false is not a synchronous cap4k Handler contract")
        }
        require(
            !AnnotatedElementUtils.hasAnnotation(targetMethod, Async::class.java) &&
                !AnnotatedElementUtils.hasAnnotation(method, Async::class.java) &&
                !AnnotatedElementUtils.hasAnnotation(targetClass, Async::class.java)
        ) {
            invalid(
                targetClass,
                targetMethod,
                "@Async is unsupported; use Mediator-managed parallel Query/Capability work or enqueue reliable work",
            )
        }
        require(!KotlinDetector.isSuspendingFunction(targetMethod)) {
            invalid(targetClass, targetMethod, "suspend functions are not synchronous cap4k Handlers")
        }
        require(targetMethod.returnType == Void.TYPE) {
            invalid(targetClass, targetMethod, "the return type must be Unit/void")
        }
        require(declaredEventClasses.size <= 1) {
            invalid(targetClass, targetMethod, "classes must declare exactly one concrete cap4k event at most")
        }
        require(parameterClasses.size == 1) {
            invalid(targetClass, targetMethod, "the method must declare exactly one event payload parameter")
        }

        val eventPayloadClass = declaredEventClasses.singleOrNull() ?: parameterClasses.single()
        require(parameterClasses.single() == eventPayloadClass) {
            invalid(targetClass, targetMethod, "the parameter type must exactly match the declared event class")
        }
        val hasDomainMarker = eventPayloadClass.isAnnotationPresent(DomainEvent::class.java)
        val hasIntegrationMarker = eventPayloadClass.isAnnotationPresent(IntegrationEvent::class.java)
        require(hasDomainMarker.xor(hasIntegrationMarker)) {
            invalid(targetClass, targetMethod, "the payload must declare exactly one of @DomainEvent or @IntegrationEvent")
        }
        require(!eventPayloadClass.isInterface && !Modifier.isAbstract(eventPayloadClass.modifiers)) {
            invalid(targetClass, targetMethod, "the payload must be a concrete event type; polymorphic subscriptions are unsupported")
        }

        return Cap4kEventHandlerDescriptor(
            beanName = beanName,
            targetClass = targetClass,
            method = targetMethod,
            eventPayloadClass = eventPayloadClass,
            eventKind = if (hasDomainMarker) Cap4kEventKind.DOMAIN else Cap4kEventKind.INTEGRATION,
            condition = eventListener.condition,
            order = AnnotatedElementUtils.findMergedAnnotation(targetMethod, Order::class.java)?.value
                ?: Ordered.LOWEST_PRECEDENCE,
        )
    }

    fun resolveMethods(beanName: String, targetClass: Class<*>): List<Cap4kEventHandlerDescriptor> {
        val descriptors: Map<Method, Cap4kEventHandlerDescriptor?> = MethodIntrospector.selectMethods(
            targetClass,
            MethodIntrospector.MetadataLookup { method -> resolve(beanName, targetClass, method) },
        )
        return descriptors.values
            .filterNotNull()
            .distinctBy { descriptor -> descriptor.method.toGenericString() }
    }

    private fun isCap4kEvent(type: Class<*>): Boolean =
        type.isAnnotationPresent(DomainEvent::class.java) ||
            type.isAnnotationPresent(IntegrationEvent::class.java)

    private fun invalid(targetClass: Class<*>, method: Method, reason: String): String =
        "Invalid cap4k event Handler ${targetClass.name}#${method.name}: $reason"
}
