package com.only4.cap4k.ddd.application.event

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.parser.Feature
import com.only4.cap4k.ddd.application.event.commands.IntegrationEventHttpSubscribeCommand
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptor
import com.only4.cap4k.ddd.core.domain.event.EventSubscriberManager
import com.only4.cap4k.ddd.core.share.misc.findIntegrationEventClasses
import com.only4.cap4k.ddd.core.share.misc.resolvePlaceholderWithCache
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.OrderUtils
import org.springframework.core.env.Environment
import org.springframework.messaging.support.GenericMessage

/**
 * 自动处理集成事件回调
 *
 * @author binking338
 * @date 2025/5/19
 */
class HttpIntegrationEventSubscriberAdapter(
    private val eventSubscriberManager: EventSubscriberManager,
    private val eventMessageInterceptors: List<EventMessageInterceptor>,
    private val httpIntegrationEventSubscriberRegister: HttpIntegrationEventSubscriberRegister,
    private val environment: Environment,
    private val scanPath: String,
    private val applicationName: String,
    private val httpBaseUrl: String,
    private val httpSubscribePath: String,
    private val httpConsumePath: String
) {
    private val logger = LoggerFactory.getLogger(HttpIntegrationEventSubscriberAdapter::class.java)
    private val eventPayloadClassMap = mutableMapOf<String, Class<*>>()

    // 延迟初始化的排序拦截器
    private val orderedEventMessageInterceptors by lazy {
        eventMessageInterceptors.sortedBy { interceptor ->
            OrderUtils.getOrder(interceptor.javaClass, Ordered.LOWEST_PRECEDENCE)
        }
    }

    fun init() {
        val classes = findIntegrationEventClasses(scanPath)

        classes.filter { cls ->
            val integrationEvent = cls.getAnnotation(IntegrationEvent::class.java)
            integrationEvent != null &&
                    integrationEvent.value.isNotBlank() &&
                    !IntegrationEvent.NONE_SUBSCRIBER.equals(integrationEvent.subscriber, ignoreCase = true)
        }.forEach { integrationEventClass ->
            val integrationEvent = integrationEventClass.getAnnotation(IntegrationEvent::class.java)
            val isRemote = integrationEvent.value.contains("@")

            val subscriber = integrationEvent.subscriber.ifBlank {
                applicationName
            }.let { resolvePlaceholderWithCache(it, environment) }

            val target = integrationEvent.value.split("@")[0]
                .let { resolvePlaceholderWithCache(it, environment) }

            val eventSourceRegisterUrl = if (isRemote) {
                integrationEvent.value.split("@")[1]
            } else {
                httpBaseUrl + httpSubscribePath
            }

            val eventCallbackUrl = httpBaseUrl + httpConsumePath

            if (!isRemote) {
                httpIntegrationEventSubscriberRegister.subscribe(target, subscriber, eventCallbackUrl)
            } else {
                Mediator.commands.send(
                    IntegrationEventHttpSubscribeCommand.Request(
                        url = eventSourceRegisterUrl,
                        event = target,
                        subscriber = subscriber,
                        callbackUrl = eventCallbackUrl
                    )
                )
            }
            eventPayloadClassMap[target] = integrationEventClass
        }
    }

    fun consume(event: String, payloadJsonStr: String, headers: Map<String, Any> = emptyMap()): Boolean {
        return runCatching {
            val integrationEventClass = eventPayloadClassMap[event]
                ?: run {
                    logger.error("集成事件消费失败, $event : $payloadJsonStr")
                    return false
                }

            val eventPayload = JSON.parseObject(
                payloadJsonStr, integrationEventClass, Feature.SupportNonPublicField
            )

            if (orderedEventMessageInterceptors.isEmpty()) {
                eventSubscriberManager.dispatch(eventPayload)
            } else {
                val message = GenericMessage(
                    eventPayload,
                    EventMessageInterceptor.ModifiableMessageHeaders(headers)
                )

                orderedEventMessageInterceptors.forEach { it.preSubscribe(message) }
                // 拦截器可能修改消息，重新获取
                val modifiedPayload = message.payload
                eventSubscriberManager.dispatch(modifiedPayload)
                orderedEventMessageInterceptors.forEach { it.postSubscribe(message) }
            }
            true
        }.onFailure { ex ->
            logger.error("集成事件消费失败, $event : $payloadJsonStr", ex)
        }.getOrDefault(false)
    }

    /**
     * 操作响应数据类
     */
    data class OperationResponse<T : Any>(
        val success: Boolean = false,
        val message: String? = null,
        val data: T? = null
    )
}
