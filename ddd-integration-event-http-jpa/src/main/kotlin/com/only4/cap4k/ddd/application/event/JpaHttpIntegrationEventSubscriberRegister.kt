package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.application.event.persistence.EventHttpSubscriber
import com.only4.cap4k.ddd.application.event.persistence.EventHttpSubscriberJpaRepository

/**
 * JPA集成事件订阅注册器实现
 *
 * @author binking338
 * @date 2025/5/23
 */
class JpaHttpIntegrationEventSubscriberRegister(
    private val eventHttpSubscriberJpaRepository: EventHttpSubscriberJpaRepository
) : HttpIntegrationEventSubscriberRegister {

    override fun subscribe(event: String, subscriber: String, callbackUrl: String): Boolean {
        val existingSubscriber = eventHttpSubscriberJpaRepository.findOne { root, _, cb ->
            cb.and(
                cb.equal(root.get<String>(EventHttpSubscriber.F_EVENT), event),
                cb.equal(root.get<String>(EventHttpSubscriber.F_SUBSCRIBER), subscriber)
            )
        }

        if (existingSubscriber.isPresent) {
            return false
        }

        val eventHttpSubscriber = EventHttpSubscriber(
            id = null,
            event = event,
            subscriber = subscriber,
            callbackUrl = callbackUrl,
            version = 0
        )

        eventHttpSubscriberJpaRepository.saveAndFlush(eventHttpSubscriber)
        return true
    }

    override fun unsubscribe(event: String, subscriber: String): Boolean {
        val eventHttpSubscriber = eventHttpSubscriberJpaRepository.findOne { root, _, cb ->
            cb.and(
                cb.equal(root.get<String>(EventHttpSubscriber.F_EVENT), event),
                cb.equal(root.get<String>(EventHttpSubscriber.F_SUBSCRIBER), subscriber)
            )
        }

        if (eventHttpSubscriber.isEmpty) {
            return false
        }

        eventHttpSubscriberJpaRepository.delete(eventHttpSubscriber.get())
        eventHttpSubscriberJpaRepository.flush()
        return true
    }

    override fun events(): List<String> {
        return eventHttpSubscriberJpaRepository.findAll()
            .map { it.event }
            .distinct()
    }

    override fun subscribers(event: String): List<HttpIntegrationEventSubscriberRegister.SubscriberInfo> {
        return eventHttpSubscriberJpaRepository.findAll { root, _, cb ->
            cb.equal(root.get<String>(EventHttpSubscriber.F_EVENT), event)
        }.map { eventHttpSubscriber ->
            HttpIntegrationEventSubscriberRegister.SubscriberInfo(
                event = eventHttpSubscriber.event,
                subscriber = eventHttpSubscriber.subscriber,
                callbackUrl = eventHttpSubscriber.callbackUrl
            )
        }
    }
}
