package com.only4.cap4k.ddd.core.domain.event.impl

import com.only4.cap4k.ddd.core.domain.event.EventSubscriber
import com.only4.cap4k.ddd.core.domain.event.EventSubscriberManager
import com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass
import org.springframework.context.ApplicationEventPublisher
import org.springframework.aop.support.AopUtils
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.scheduling.annotation.Async
import java.util.concurrent.ConcurrentHashMap

/**
 * 默认事件订阅管理器，负责领域事件和集成事件的本地订阅分发与 Spring 事件桥接
 *
 * @author LD_moxeii
 * @date 2025/07/24
 */
class DefaultEventSubscriberManager(
    private val subscribers: List<EventSubscriber<*>>,
    private val applicationEventPublisher: ApplicationEventPublisher,
) : EventSubscriberManager {

    private val subscriberMap by lazy {
        ConcurrentHashMap<Class<*>, MutableList<EventSubscriber<*>>>().also {
            initializeSubscribers(it)
        }
    }

    fun init() {
        // 预热subscriberMap，触发lazy初始化
        subscriberMap
    }

    private fun initializeSubscribers(subscriberMap: MutableMap<Class<*>, MutableList<EventSubscriber<*>>>) {
        // Registration order is an implementation detail, not a reaction-order contract.
        subscribers.forEach { subscriber ->
            validateSynchronousSubscriber(subscriber)
            val eventClass = resolveGenericTypeClass(
                subscriber, 0,
                AbstractEventSubscriber::class.java, EventSubscriber::class.java
            )
            subscribeInternal(subscriberMap, eventClass, subscriber)
        }

    }

    override fun subscribe(eventPayloadClass: Class<*>, subscriber: EventSubscriber<*>): Boolean {
        return subscribeInternal(subscriberMap, eventPayloadClass, subscriber)
    }

    private fun subscribeInternal(
        map: MutableMap<Class<*>, MutableList<EventSubscriber<*>>>,
        eventPayloadClass: Class<*>,
        subscriber: EventSubscriber<*>
    ): Boolean =
        map.computeIfAbsent(eventPayloadClass) {
            mutableListOf()
        }.add(subscriber)


    override fun unsubscribe(eventPayloadClass: Class<*>, subscriber: EventSubscriber<*>): Boolean =
        subscriberMap[eventPayloadClass]?.remove(subscriber) ?: false


    override fun dispatch(eventPayload: Any) {
        subscriberMap[eventPayload.javaClass].orEmpty().forEach { subscriber ->
            val subscriberClass = AopUtils.getTargetClass(subscriber)
            try {
                EventRuntimeContext.withCausalFrame("Handler:${subscriberClass.name}") {
                    @Suppress("UNCHECKED_CAST")
                    (subscriber as EventSubscriber<Any>).onEvent(eventPayload)
                }
            } catch (ex: Exception) {
                throw EventDispatchException(
                    eventPayload.javaClass,
                    EventDispatchException.snapshot(EventRuntimeContext.currentOrNull()),
                    listOf(EventSubscriberFailure(subscriberClass, ex)),
                )
            }
        }
        try {
            applicationEventPublisher.publishEvent(eventPayload)
        } catch (ex: Exception) {
            throw EventDispatchException(
                eventPayload.javaClass,
                EventDispatchException.snapshot(EventRuntimeContext.currentOrNull()),
                listOf(EventSubscriberFailure(applicationEventPublisher.javaClass, ex)),
            )
        }
    }

    private fun validateSynchronousSubscriber(subscriber: EventSubscriber<*>) {
        val targetClass = AopUtils.getTargetClass(subscriber)
        val asyncMethod = targetClass.methods.firstOrNull { method ->
            method.name == EventSubscriber<*>::onEvent.name &&
                method.parameterCount == 1 &&
                AnnotatedElementUtils.hasAnnotation(method, Async::class.java)
        }
        check(
            !AnnotatedElementUtils.hasAnnotation(targetClass, Async::class.java) && asyncMethod == null
        ) {
            "Cap4k synchronous EventSubscriber ${targetClass.name} cannot use @Async; " +
                "enqueue a reliable Command or publish an Integration Event instead"
        }
    }
}
