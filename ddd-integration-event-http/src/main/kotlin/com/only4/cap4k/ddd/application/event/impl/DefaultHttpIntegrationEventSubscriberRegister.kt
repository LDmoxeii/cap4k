package com.only4.cap4k.ddd.application.event.impl

import com.only4.cap4k.ddd.application.event.HttpIntegrationEventSubscriberRegister
import com.only4.cap4k.ddd.application.event.HttpIntegrationEventSubscriberRegister.SubscriberInfo
import java.util.concurrent.ConcurrentHashMap

/**
 * 集成事件订阅注册器默认实现
 *
 * @author binking338
 * @date 2025/5/21
 */
class DefaultHttpIntegrationEventSubscriberRegister : HttpIntegrationEventSubscriberRegister {

    private val subscriberMap = ConcurrentHashMap<String, MutableMap<String, String>>()

    override fun subscribe(event: String, subscriber: String, callbackUrl: String): Boolean {
        val eventSubscriberMap = subscriberMap.computeIfAbsent(event) { mutableMapOf() }

        return synchronized(eventSubscriberMap) {
            if (eventSubscriberMap.containsKey(subscriber)) {
                false
            } else {
                eventSubscriberMap[subscriber] = callbackUrl
                true
            }
        }
    }

    override fun unsubscribe(event: String, subscriber: String): Boolean {
        val eventSubscriberMap = subscriberMap[event] ?: return false

        return synchronized(eventSubscriberMap) {
            eventSubscriberMap.remove(subscriber) != null
        }
    }

    override fun events(): List<String> {
        return subscriberMap.keys.toList()
    }

    override fun subscribers(event: String): List<SubscriberInfo> {
        val eventSubscriberMap = subscriberMap[event] ?: return emptyList()

        return synchronized(eventSubscriberMap) {
            eventSubscriberMap.map { (subscriber, callbackUrl) ->
                SubscriberInfo(
                    event = event,
                    subscriber = subscriber,
                    callbackUrl = callbackUrl
                )
            }
        }
    }
}
