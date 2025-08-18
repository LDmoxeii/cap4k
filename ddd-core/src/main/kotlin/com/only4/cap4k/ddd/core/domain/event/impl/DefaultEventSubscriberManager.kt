package com.only4.cap4k.ddd.core.domain.event.impl

import com.only4.cap4k.ddd.core.application.RequestParam
import com.only4.cap4k.ddd.core.application.RequestSupervisor
import com.only4.cap4k.ddd.core.application.event.IntegrationEventSupervisor
import com.only4.cap4k.ddd.core.application.event.annotation.AutoRelease
import com.only4.cap4k.ddd.core.application.event.annotation.AutoReleases
import com.only4.cap4k.ddd.core.application.event.annotation.AutoRequest
import com.only4.cap4k.ddd.core.application.event.annotation.AutoRequests
import com.only4.cap4k.ddd.core.domain.event.EventSubscriber
import com.only4.cap4k.ddd.core.domain.event.EventSubscriberManager
import com.only4.cap4k.ddd.core.share.misc.findDomainEventClasses
import com.only4.cap4k.ddd.core.share.misc.findIntegrationEventClasses
import com.only4.cap4k.ddd.core.share.misc.newConverterInstance
import com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.Ordered
import org.springframework.core.annotation.OrderUtils
import org.springframework.core.convert.converter.Converter
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * 基于RocketMq的领域事件订阅管理器
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

            // 自动实现 Event -> Request
            processAutoRequests(subscriberMap, domainEventClass)
        }

        // 处理集成事件
        findIntegrationEventClasses(scanPath).forEach { integrationEventClass ->
            subscribeInternal(subscriberMap, integrationEventClass) { event ->
                applicationEventPublisher.publishEvent(event)
            }

            // 自动实现 DomainEvent -> IntegrationEvent 适配
            processAutoReleases(subscriberMap, integrationEventClass)

            // 自动实现 Event -> Request 转发
            processAutoRequests(subscriberMap, integrationEventClass)
        }
    }

    private fun processAutoRequests(map: MutableMap<Class<*>, MutableList<EventSubscriber<*>>>, eventClass: Class<*>) {
        val autoRequests = mutableListOf<AutoRequest>()

        eventClass.getAnnotation(AutoRequest::class.java)?.let { autoRequests.add(it) }
        eventClass.getAnnotation(AutoRequests::class.java)?.let {
            autoRequests.addAll(it.value)
        }

        autoRequests.forEach { autoRequest ->
            val converterClass = if (Converter::class.java.isAssignableFrom(autoRequest.converterClass.java)) {
                autoRequest.converterClass
            } else null

            val converter = newConverterInstance(
                eventClass,
                autoRequest.targetRequestClass.java,
                converterClass?.java
            )

            subscribeInternal(map, eventClass) { event ->
                val requestParam = converter.convert(event) as RequestParam<*>
                RequestSupervisor.instance.send(requestParam)
            }
        }
    }

    private fun processAutoReleases(
        map: MutableMap<Class<*>, MutableList<EventSubscriber<*>>>,
        integrationEventClass: Class<*>
    ) {
        val autoReleases = mutableListOf<AutoRelease>()

        integrationEventClass.getAnnotation(AutoRelease::class.java)?.let { autoReleases.add(it) }
        integrationEventClass.getAnnotation(AutoReleases::class.java)?.let {
            autoReleases.addAll(it.value)
        }

        autoReleases.forEach { autoRelease ->
            val converterClass = when {
                Converter::class.java.isAssignableFrom(integrationEventClass) -> integrationEventClass
                Converter::class.java.isAssignableFrom(autoRelease.converterClass.java) -> autoRelease.converterClass.java
                else -> null
            }

            val converter = newConverterInstance(
                autoRelease.sourceDomainEventClass.java,
                integrationEventClass,
                converterClass
            )

            subscribeInternal(map, autoRelease.sourceDomainEventClass.java) { domainEvent ->
                val integrationEvent = converter.convert(domainEvent)!!
                IntegrationEventSupervisor.instance.publish(
                    integrationEvent,
                    Duration.ofSeconds(autoRelease.delayInSeconds)
                )
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

        val exceptions = mutableListOf<Exception>()
        subscribersForEvent.forEach { subscriber ->
            try {
                @Suppress("UNCHECKED_CAST")
                (subscriber as EventSubscriber<Any>).onEvent(eventPayload)
            } catch (ex: Exception) {
                // 记录异常但不影响其他订阅器的执行
                exceptions.add(ex)
                // 可以根据需要添加日志记录
                // log.error("Subscriber ${subscriber.javaClass.simpleName} failed to handle event", ex)
            }
        }
        if (exceptions.isNotEmpty()) {
            throw RuntimeException("Some subscribers failed to handle the event: ${exceptions.joinToString(", ")}")
        }
    }
}
