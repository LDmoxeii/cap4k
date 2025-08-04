package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.application.event.commands.IntegrationEventHttpCallbackTriggerCommand
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.share.misc.createFixedThreadPool
import com.only4.cap4k.ddd.core.share.misc.resolvePlaceholderWithCache
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import java.util.concurrent.ExecutorService

/**
 * 基于Http的集成事件发布器
 *
 * @author binking338
 * @date 2025/5/19
 */
class HttpIntegrationEventPublisher(
    private val subscriberRegister: HttpIntegrationEventSubscriberRegister,
    private val environment: Environment,
    private val threadPoolSize: Int = 10,
    private val threadFactoryClassName: String = ""
) : IntegrationEventPublisher {

    private val logger = LoggerFactory.getLogger(HttpIntegrationEventPublisher::class.java)

    private val executorService: ExecutorService by lazy {
        createFixedThreadPool(
            threadPoolSize,
            threadFactoryClassName,
            javaClass.classLoader
        )
    }


    fun init() {
        executorService
    }

    override fun publish(event: EventRecord, publishCallback: IntegrationEventPublisher.PublishCallback) {
        val destination = event.type

        val resolvedDestination = resolvePlaceholderWithCache(destination, environment)
            .split("@")[0]

        val subscribers = subscriberRegister.subscribers(resolvedDestination)

        if (subscribers.isNotEmpty()) {
            executorService.execute {
                runCatching {
                    subscribers.forEach { subscriber ->
                        Mediator.commands.async(
                            IntegrationEventHttpCallbackTriggerCommand.Request(
                                url = subscriber.callbackUrl,
                                uuid = event.id,
                                event = resolvedDestination,
                                payload = event.payload
                            )
                        )
                    }
                    publishCallback.onSuccess(event)
                }.onFailure { throwable ->
                    logger.error("集成事件发布失败, ${event.id}", throwable)
                    publishCallback.onException(event, throwable)
                }
            }
        }
    }
}
