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
    private val log = LoggerFactory.getLogger(HttpIntegrationEventSubscriberAdapter::class.java)
    private val eventPayloadClassMap = mutableMapOf<String, Class<*>>()

    private val orderedEventMessageInterceptors by lazy {
        eventMessageInterceptors.sortedBy { interceptor ->
            OrderUtils.getOrder(interceptor.javaClass, Ordered.LOWEST_PRECEDENCE)
        }
    }

    fun init() {
        findIntegrationEventClasses(scanPath)
            .filter { cls ->
                val integrationEvent = cls.getAnnotation(IntegrationEvent::class.java)
                integrationEvent.value.isNotBlank() &&
                        !IntegrationEvent.NONE_SUBSCRIBER.equals(integrationEvent.subscriber, ignoreCase = true)
            }
            .map { cls -> createEventRegistration(cls) }
            .forEach { registration -> registerEvent(registration) }
    }

    private fun createEventRegistration(eventClass: Class<*>): EventRegistration {
        val annotation = eventClass.getAnnotation(IntegrationEvent::class.java)

        val isRemote = annotation.value.contains("@")
        val targetAndUrl = annotation.value.split("@")
        val target = resolvePlaceholderWithCache(targetAndUrl[0], environment)

        val subscriber = annotation.subscriber.ifBlank { applicationName }
            .let { resolvePlaceholderWithCache(it, environment) }

        val registerUrl = if (isRemote && targetAndUrl.size > 1) {
            targetAndUrl[1]
        } else {
            httpBaseUrl + httpSubscribePath
        }

        val callbackUrl = httpBaseUrl + httpConsumePath

        return EventRegistration(
            eventClass = eventClass,
            target = target,
            subscriber = subscriber,
            isRemote = isRemote,
            registerUrl = registerUrl,
            callbackUrl = callbackUrl
        )
    }

    private fun registerEvent(registration: EventRegistration) {
        if (!registration.isRemote) {
            httpIntegrationEventSubscriberRegister.subscribe(
                registration.target,
                registration.subscriber,
                registration.callbackUrl
            )
        } else {
            Mediator.commands.send(
                IntegrationEventHttpSubscribeCommand.Request(
                    url = registration.registerUrl,
                    event = registration.target,
                    subscriber = registration.subscriber,
                    callbackUrl = registration.callbackUrl
                )
            )
        }
        eventPayloadClassMap[registration.target] = registration.eventClass
    }

    fun consume(event: String, payloadJsonStr: String, headers: Map<String, Any> = emptyMap()): Boolean =
        runCatching {
            val integrationEventClass = eventPayloadClassMap[event]
                ?: return logAndReturnFailure("未找到事件类型映射", event, payloadJsonStr)

            val eventPayload = parseEventPayload(payloadJsonStr, integrationEventClass)
                ?: return logAndReturnFailure("事件载荷解析失败", event, payloadJsonStr)

            processEventWithInterceptors(eventPayload, headers)
            true
        }.onFailure { ex ->
            log.error("集成事件消费失败, event: $event, payload: $payloadJsonStr", ex)
        }.getOrDefault(false)

    private fun logAndReturnFailure(reason: String, event: String, payloadJsonStr: String): Boolean {
        log.error("集成事件消费失败 - $reason, event: $event, payload: $payloadJsonStr")
        return false
    }

    private fun parseEventPayload(payloadJsonStr: String, eventClass: Class<*>): Any? {
        return try {
            JSON.parseObject(payloadJsonStr, eventClass, Feature.SupportNonPublicField)
        } catch (ex: Exception) {
            log.error("JSON解析失败: $payloadJsonStr", ex)
            null
        }
    }

    private fun processEventWithInterceptors(eventPayload: Any, headers: Map<String, Any>) {
        if (orderedEventMessageInterceptors.isEmpty()) {
            eventSubscriberManager.dispatch(eventPayload)
            return
        }

        val message = GenericMessage(
            eventPayload,
            EventMessageInterceptor.ModifiableMessageHeaders(headers)
        )

        orderedEventMessageInterceptors.forEach { it.preSubscribe(message) }

        // 拦截器可能修改消息，重新获取载荷
        val modifiedPayload = message.payload
        eventSubscriberManager.dispatch(modifiedPayload)

        orderedEventMessageInterceptors.forEach { it.postSubscribe(message) }
    }

    private data class EventRegistration(
        val eventClass: Class<*>,
        val target: String,
        val subscriber: String,
        val isRemote: Boolean,
        val registerUrl: String,
        val callbackUrl: String
    )

    /**
     * 操作响应数据类
     */
    data class OperationResponse<T : Any>(
        val success: Boolean = false,
        val message: String? = null,
        val data: T? = null
    )
}
