package com.only4.cap4k.ddd.core.domain.event.impl

import com.only4.cap4k.ddd.core.domain.event.EventSubscriber
import com.only4.cap4k.ddd.core.domain.event.EventSubscriberManager
import com.only4.cap4k.ddd.core.share.misc.findDomainEventClasses
import com.only4.cap4k.ddd.core.share.misc.findIntegrationEventClasses
import com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.Ordered
import org.springframework.core.annotation.OrderUtils
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
    private val scanPath: String
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
        // 按照Order排序订阅者
        val sortedSubscribers = subscribers.sortedBy { subscriber ->
            OrderUtils.getOrder(subscriber.javaClass, Ordered.LOWEST_PRECEDENCE)
        }

        // 注册订阅者
        sortedSubscribers.forEach { subscriber ->
            val eventClass = resolveGenericTypeClass(
                subscriber, 0,
                AbstractEventSubscriber::class.java, EventSubscriber::class.java
            )
            subscribeInternal(subscriberMap, eventClass, subscriber)
        }

        // 处理领域事件
        findDomainEventClasses(scanPath).forEach { domainEventClass ->
            // 自动实现 Spring EventListener 适配
            subscribeInternal(subscriberMap, domainEventClass) { event ->
                applicationEventPublisher.publishEvent(event)
            }
        }

        // 处理集成事件
        findIntegrationEventClasses(scanPath).forEach { integrationEventClass ->
            subscribeInternal(subscriberMap, integrationEventClass) { event ->
                applicationEventPublisher.publishEvent(event)
            }
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
        val subscribersForEvent = subscriberMap[eventPayload.javaClass] ?: return

        if (subscribersForEvent.isEmpty()) return

        val failures = mutableListOf<EventSubscriberFailure>()
        subscribersForEvent.forEach { subscriber ->
            try {
                @Suppress("UNCHECKED_CAST")
                (subscriber as EventSubscriber<Any>).onEvent(eventPayload)
            } catch (ex: Exception) {
                // 记录异常但不影响其他订阅器的执行
                failures.add(EventSubscriberFailure(subscriber.javaClass, ex))
                // 可以根据需要添加日志记录
                // log.error("Subscriber ${subscriber.javaClass.simpleName} failed to handle event", ex)
            }
        }
        if (failures.isNotEmpty()) {
            throw EventDispatchException(
                eventPayload.javaClass,
                EventDispatchException.snapshot(EventRuntimeContext.currentOrNull()),
                failures
            )
        }
    }
}
