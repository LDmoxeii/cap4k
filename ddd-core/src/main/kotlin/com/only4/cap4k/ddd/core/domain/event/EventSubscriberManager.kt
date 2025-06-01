package com.only4.cap4k.ddd.core.domain.event

import com.only4.cap4k.ddd.core.application.RequestParam
import com.only4.cap4k.ddd.core.application.RequestSupervisor
import com.only4.cap4k.ddd.core.application.event.IntegrationEventSupervisor
import com.only4.cap4k.ddd.core.application.event.annotation.AutoRelease
import com.only4.cap4k.ddd.core.application.event.annotation.AutoRequest
import com.only4.cap4k.ddd.core.share.misc.ClassUtils
import com.only4.cap4k.ddd.core.share.misc.ScanUtils
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.Ordered
import org.springframework.core.annotation.OrderUtils
import org.springframework.core.convert.converter.Converter
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * 领域事件订阅管理器接口
 *
 * @author binking338
 * @date 2023/8/13
 */
interface EventSubscriberManager {
    /**
     * 订阅事件
     *
     * @param eventPayloadClass
     * @param subscriber
     * @return
     */
    fun subscribe(
        eventPayloadClass: Class<*>,
        subscriber: EventSubscriber<*>
    ): Boolean

    /**
     * 取消订阅
     *
     * @param eventPayloadClass
     * @param subscriber
     * @return
     */
    fun unsubscribe(
        eventPayloadClass: Class<*>,
        subscriber: EventSubscriber<*>
    ): Boolean

    /**
     * 分发事件到所有订阅者
     *
     * @param eventPayload
     */
    fun dispatch(eventPayload: Any)
}

/**
 * 默认的领域事件订阅管理器实现
 *
 * @author binking338
 * @date 2023/8/13
 */
class DefaultEventSubscriberManager(
    private val subscribers: List<EventSubscriber<*>>,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val scanPath: String
) : EventSubscriberManager {

    private var subscriberMap: MutableMap<Class<*>, MutableList<EventSubscriber<*>>> = ConcurrentHashMap()

    private val initialized by lazy {
        subscribers.sortedBy { OrderUtils.getOrder(it.javaClass, Ordered.LOWEST_PRECEDENCE) }
            .forEach { subscriber ->
                val eventClass = ClassUtils.resolveGenericTypeClass(
                    subscriber, 0,
                    AbstractEventSubscriber::class.java, EventSubscriber::class.java
                )
                subscribe(eventClass, subscriber)
            }

        // 处理领域事件
        ScanUtils.findDomainEventClasses(scanPath).forEach { domainEventClass ->
            // Spring EventListener适配
            subscribe(domainEventClass, applicationEventPublisher::publishEvent)

            // 自动实现Event -> Request
            domainEventClass.getAnnotationsByType(AutoRequest::class.java).toList()
                .forEach { autoRequest ->
                    val converterClass =
                        if (Converter::class.java.isAssignableFrom(autoRequest.converterClass.java)) {
                            autoRequest.converterClass.java
                        } else Unit::class.java

                    val converter = ClassUtils.newConverterInstance(
                        domainEventClass,
                        autoRequest.targetRequestClass.java,
                        converterClass
                    )

                    subscribe(domainEventClass) { domainEvent ->
                        RequestSupervisor.instance.send(converter.convert(domainEvent) as RequestParam<*>)
                    }
                }
        }

        // 处理集成事件
        ScanUtils.findIntegrationEventClasses(scanPath).forEach { integrationEventClass ->
            subscribe(integrationEventClass, applicationEventPublisher::publishEvent)

            // 自动实现DomainEvent -> IntegrationEvent适配
            val autoReleases = integrationEventClass.getAnnotationsByType(AutoRelease::class.java).toList()
            autoReleases.forEach { autoRelease ->
                val converterClass = when {
                    Converter::class.java.isAssignableFrom(integrationEventClass) -> integrationEventClass
                    Converter::class.java.isAssignableFrom(autoRelease.converterClass.java) -> autoRelease.converterClass.java
                    else -> Unit::class.java
                }

                val converter = ClassUtils.newConverterInstance(
                    autoRelease.sourceDomainEventClass.java,
                    integrationEventClass,
                    converterClass
                )

                subscribe(autoRelease.sourceDomainEventClass.java) { domainEvent ->
                    IntegrationEventSupervisor.instance.attach(
                        converter.convert(domainEvent)!!,
                        delay = Duration.ofSeconds(autoRelease.delayInSeconds)
                    )
                    IntegrationEventSupervisor.manager.release()
                }
            }

            // 自动实现Event -> Request转发
            val autoRequests = integrationEventClass.getAnnotationsByType(AutoRequest::class.java).toList()
            if (autoRequests.isNotEmpty()) {
                autoRequests.forEach { autoRequest ->
                    val converterClass = if (Converter::class.java.isAssignableFrom(autoRequest.converterClass.java)) {
                        autoRequest.converterClass.java
                    } else Unit::class.java

                    val converter = ClassUtils.newConverterInstance(
                        integrationEventClass,
                        autoRequest.targetRequestClass.java,
                        converterClass
                    )

                    subscribe(integrationEventClass) { integrationEvent ->
                        RequestSupervisor.instance.send(converter.convert(integrationEvent) as RequestParam<*>)
                    }
                }
            }
        }
        true
    }

    override fun subscribe(eventPayloadClass: Class<*>, subscriber: EventSubscriber<*>): Boolean {
        return subscriberMap.computeIfAbsent(eventPayloadClass) { mutableListOf() }.add(subscriber)
    }

    override fun unsubscribe(eventPayloadClass: Class<*>, subscriber: EventSubscriber<*>): Boolean {
        return subscriberMap[eventPayloadClass]?.remove(subscriber) ?: false
    }

    @Suppress("UNCHECKED_CAST")
    override fun dispatch(eventPayload: Any) {
        initialized // 触发懒加载初始化
        subscriberMap[eventPayload.javaClass]?.forEach { subscriber ->
            (subscriber as EventSubscriber<Any>).onEvent(eventPayload)
        }
    }
}
