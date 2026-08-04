package com.only4.cap4k.ddd.application.event

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.parser.Feature
import com.only4.cap4k.ddd.application.event.capabilities.IntegrationEventHttpSubscribeCapability
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.application.context.ExecutionContextBoundary
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextScopeManager
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContext
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContextScopeManager
import com.only4.cap4k.ddd.core.domain.event.ReliableEventRedeliveryHint
import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptor
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.EventTypeCatalog
import com.only4.cap4k.ddd.core.share.misc.resolvePlaceholderWithCache
import com.only4.cap4k.ddd.core.share.Constants.HEADER_KEY_CAP4K_EXECUTION_CONTEXT
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.OrderUtils
import org.springframework.core.env.Environment
import org.springframework.messaging.support.GenericMessage
import java.time.Instant

/**
 * 自动处理集成事件回调
 *
 * @author binking338
 * @date 2025/5/19
 */
class HttpIntegrationEventSubscriberAdapter(
    private val eventHandlerDispatcher: EventHandlerDispatcher,
    private val eventMessageInterceptors: List<EventMessageInterceptor>,
    private val httpIntegrationEventSubscriberRegister: HttpIntegrationEventSubscriberRegister,
    private val environment: Environment,
    private val eventTypeCatalog: EventTypeCatalog,
    private val applicationName: String,
    private val httpBaseUrl: String,
    private val httpSubscribePath: String,
    private val httpConsumePath: String,
    private val executionContextCodecRegistry: ExecutionContextCodecRegistry = ExecutionContextCodecRegistry(emptyList()),
    private val executionContextScopeManager: ExecutionContextScopeManager = ExecutionContextScopeManager {
        AutoCloseable { }
    },
    private val reliableEventDeliveryContextScopeManager: ReliableEventDeliveryContextScopeManager,
) {
    private val log = LoggerFactory.getLogger(HttpIntegrationEventSubscriberAdapter::class.java)
    private val eventPayloadClassMap = mutableMapOf<String, Class<*>>()

    private val orderedEventMessageInterceptors by lazy {
        eventMessageInterceptors.sortedBy { interceptor ->
            OrderUtils.getOrder(interceptor.javaClass, Ordered.LOWEST_PRECEDENCE)
        }
    }

    fun init() {
        eventTypeCatalog.integrationEventTypes()
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
            Mediator.capabilities.call(
                IntegrationEventHttpSubscribeCapability.Request(
                    url = registration.registerUrl,
                    event = registration.target,
                    subscriber = registration.subscriber,
                    callbackUrl = registration.callbackUrl
                )
            )
        }
        eventPayloadClassMap[registration.target] = registration.eventClass
    }

    fun consume(
        eventId: String,
        eventName: String,
        publishedAt: Instant,
        payloadJsonStr: String,
        headers: Map<String, Any> = emptyMap(),
    ): Boolean = runCatching {
        require(eventId.isNotBlank()) { "eventId must not be blank" }
        require(eventName.isNotBlank()) { "eventName must not be blank" }

        val integrationEventClass = eventPayloadClassMap[eventName]
            ?: return logAndReturnFailure("未找到事件类型映射", eventName, payloadJsonStr)

        val eventPayload = parseEventPayload(payloadJsonStr, integrationEventClass)
            ?: return logAndReturnFailure("事件载荷解析失败", eventName, payloadJsonStr)

        val deliveryContext = ReliableEventDeliveryContext(
            eventId = eventId,
            eventName = integrationEventClass.simpleName
                .takeIf(String::isNotBlank)
                ?: error("Integration event payload class must have a simple name"),
            publishedAt = publishedAt,
            attempt = null,
            redeliveryHint = ReliableEventRedeliveryHint.UNKNOWN,
        )
        val encodedExecutionContext = headers.entries
            .firstOrNull { (name, _) -> name.equals(HEADER_KEY_CAP4K_EXECUTION_CONTEXT, ignoreCase = true) }
            ?.value
        val executionContext = executionContextCodecRegistry.decodeExternal(
            IntegrationEventExecutionContextEnvelope.decode(encodedExecutionContext),
            ExecutionContextBoundary.INTEGRATION_EVENT,
        )
        executionContextScopeManager.install(executionContext).use {
            processEventWithInterceptors(eventPayload, headers, deliveryContext)
        }
        true
    }.onFailure { ex ->
        log.error("集成事件消费失败, event: $eventName, payload: $payloadJsonStr", ex)
    }.getOrDefault(false)

    private fun logAndReturnFailure(reason: String, eventName: String, payloadJsonStr: String): Boolean {
        log.error("集成事件消费失败 - $reason, event: $eventName, payload: $payloadJsonStr")
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

    private fun processEventWithInterceptors(
        eventPayload: Any,
        headers: Map<String, Any>,
        deliveryContext: ReliableEventDeliveryContext,
    ) {
        if (orderedEventMessageInterceptors.isEmpty()) {
            dispatchWithDeliveryContext(deliveryContext, eventPayload)
            return
        }

        val message = GenericMessage(
            eventPayload,
            EventMessageInterceptor.ModifiableMessageHeaders(headers)
        )

        reliableEventDeliveryContextScopeManager.suppress().use {
            orderedEventMessageInterceptors.forEach { it.preSubscribe(message) }
        }

        // 拦截器可能修改消息，重新获取载荷
        val modifiedPayload = message.payload
        dispatchWithDeliveryContext(deliveryContext, modifiedPayload)

        reliableEventDeliveryContextScopeManager.suppress().use {
            orderedEventMessageInterceptors.forEach { it.postSubscribe(message) }
        }
    }

    private fun dispatchWithDeliveryContext(
        deliveryContext: ReliableEventDeliveryContext,
        eventPayload: Any,
    ) {
        reliableEventDeliveryContextScopeManager.install(deliveryContext).use {
            eventHandlerDispatcher.dispatch(eventPayload)
        }
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
