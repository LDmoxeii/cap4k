package com.only4.cap4k.ddd.application.event

import com.alibaba.fastjson.JSON
import com.only4.cap4k.ddd.application.event.commands.IntegrationEventHttpCallbackTriggerCommand
import com.only4.cap4k.ddd.application.event.commands.IntegrationEventHttpSubscribeCommand
import com.only4.cap4k.ddd.application.event.commands.IntegrationEventHttpUnsubscribeCommand
import com.only4.cap4k.ddd.application.event.configure.HttpIntegrationEventAdapterProperties
import com.only4.cap4k.ddd.application.event.configure.RabbitMqIntegrationEventAdapterProperties
import com.only4.cap4k.ddd.application.event.impl.DefaultHttpIntegrationEventSubscriberRegister
import com.only4.cap4k.ddd.application.event.persistence.EventHttpSubscriberJpaRepository
import com.only4.cap4k.ddd.core.application.event.*
import com.only4.cap4k.ddd.core.application.event.impl.DefaultIntegrationEventSupervisor
import com.only4.cap4k.ddd.core.application.event.impl.IntegrationEventUnitOfWorkInterceptor
import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptor
import com.only4.cap4k.ddd.core.domain.event.EventPublisher
import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import com.only4.cap4k.ddd.core.domain.event.EventSubscriberManager
import com.only4.cap4k.ddd.core.share.Constants.CONFIG_KEY_4_ROCKETMQ_MSG_CHARSET
import com.only4.cap4k.ddd.core.share.Constants.CONFIG_KEY_4_ROCKETMQ_NAME_SERVER
import com.only4.cap4k.ddd.core.share.Constants.CONFIG_KEY_4_SVC_NAME
import com.only4.cap4k.ddd.domain.event.configure.EventProperties
import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration
import org.apache.rocketmq.spring.core.RocketMQTemplate
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.core.env.Environment
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.web.HttpRequestHandler
import org.springframework.web.client.RestTemplate
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * 集成事件自动配置类
 *
 * @author LD_moxeii
 * @date 2025/08/03
 */
@Configuration
class IntegrationEventAutoConfiguration {

    @Bean
    @Primary
    fun defaultIntegrationEventSupervisor(
        eventPublisher: EventPublisher,
        eventRecordRepository: EventRecordRepository,
        integrationEventInterceptorManager: IntegrationEventInterceptorManager,
        applicationEventPublisher: ApplicationEventPublisher,
        @Value(CONFIG_KEY_4_SVC_NAME)
        svcName: String,
    ): DefaultIntegrationEventSupervisor = DefaultIntegrationEventSupervisor(
        eventPublisher,
        eventRecordRepository,
        integrationEventInterceptorManager,
        applicationEventPublisher,
        svcName
    ).also {
        IntegrationEventSupervisorSupport.configure(it as IntegrationEventSupervisor)
        IntegrationEventSupervisorSupport.configure(it as IntegrationEventManager)
    }


    @Bean
    fun integrationEventUnitOfWorkInterceptor(
        integrationEventManager: IntegrationEventManager,
    ): IntegrationEventUnitOfWorkInterceptor = IntegrationEventUnitOfWorkInterceptor(integrationEventManager)


    @Configuration
    @ConditionalOnClass(name = ["com.only4.cap4k.ddd.application.event.HttpIntegrationEventSubscriberAdapter"])
    class HttpAdapterLauncher {

        companion object {
            private val log = LoggerFactory.getLogger(HttpAdapterLauncher::class.java)

            const val EVENT_PARAM = "event"
            const val EVENT_ID_PARAM = "uuid"
            const val SUBSCRIBER_PARAM = "subscriber"
            const val CONSUME_PATH = "/cap4k/integration-event/http/consume"
            const val SUBSCRIBE_PATH = "/cap4k/integration-event/http/subscribe"
            const val UNSUBSCRIBE_PATH = "/cap4k/integration-event/http/unsubscribe"
            const val EVENTS_PATH = "/cap4k/integration-event/http/events"
            const val SUBSCRIBERS_PATH = "/cap4k/integration-event/http/subscribers"
        }

        @Configuration
        @ConditionalOnClass(name = ["com.only4.cap4k.ddd.application.event.JpaHttpIntegrationEventSubscriberRegister"])
        @EnableJpaRepositories(
            basePackages = [
                "com.only4.cap4k.ddd.application.event.persistence"
            ]
        )
        @EntityScan(
            basePackages = [
                "com.only4.cap4k.ddd.application.event.persistence"
            ]
        )
        class JpaHttpIntegrationEventSubscriberRegisterLauncher {
            @Bean
            fun jpaHttpIntegrationEventSubscriberRegister(
                eventHttpSubscriberJpaRepository: EventHttpSubscriberJpaRepository,
            ): JpaHttpIntegrationEventSubscriberRegister {
                return JpaHttpIntegrationEventSubscriberRegister(eventHttpSubscriberJpaRepository)
            }
        }

        @Bean
        fun httpIntegrationEventCallbackTriggerCommandHandler(): IntegrationEventHttpCallbackTriggerCommand.Handler {
            return IntegrationEventHttpCallbackTriggerCommand.Handler(
                RestTemplate(),
                EVENT_PARAM,
                EVENT_ID_PARAM
            )
        }

        @Bean
        fun httpIntegrationEventSubscribeCommandHandler(): IntegrationEventHttpSubscribeCommand.Handler {
            return IntegrationEventHttpSubscribeCommand.Handler(
                RestTemplate(),
                EVENT_PARAM,
                SUBSCRIBER_PARAM
            )
        }

        @Bean
        fun httpIntegrationEventUnsubscribeCommandHandler(): IntegrationEventHttpUnsubscribeCommand.Handler {
            return IntegrationEventHttpUnsubscribeCommand.Handler(
                RestTemplate(),
                EVENT_PARAM,
                SUBSCRIBER_PARAM
            )
        }

        @Bean
        @ConditionalOnMissingBean(HttpIntegrationEventSubscriberRegister::class)
        fun httpIntegrationEventSubscriberRegister(): DefaultHttpIntegrationEventSubscriberRegister {
            return DefaultHttpIntegrationEventSubscriberRegister()
        }

        @Bean
        @ConditionalOnMissingBean(IntegrationEventPublisher::class)
        fun httpIntegrationEventPublisher(
            subscriberRegister: HttpIntegrationEventSubscriberRegister,
            environment: Environment,
            httpIntegrationEventAdapterProperties: HttpIntegrationEventAdapterProperties,
        ): HttpIntegrationEventPublisher {
            val httpIntegrationEventPublisher = HttpIntegrationEventPublisher(
                subscriberRegister,
                environment,
                httpIntegrationEventAdapterProperties.publishThreadPoolSize,
                httpIntegrationEventAdapterProperties.publishThreadFactoryClassName
            ).apply {
                init()
            }
            log.info("集成事件适配类型：HTTP")
            return httpIntegrationEventPublisher
        }

        @Bean
        fun httpIntegrationEventSubscriberAdapter(
            eventSubscriberManager: EventSubscriberManager,
            eventMessageInterceptors: List<EventMessageInterceptor>,
            httpIntegrationEventSubscriberRegister: HttpIntegrationEventSubscriberRegister,
            eventProperties: EventProperties,
            environment: Environment,
            @Value(CONFIG_KEY_4_SVC_NAME)
            svcName: String,
            @Value("\${server.port:80}")
            serverPort: String,
            @Value("\${server.servlet.context-path:}")
            serverServletContentPath: String,
        ): HttpIntegrationEventSubscriberAdapter {
            val baseUrl = "http://localhost:$serverPort$serverServletContentPath"
            return HttpIntegrationEventSubscriberAdapter(
                eventSubscriberManager,
                eventMessageInterceptors,
                httpIntegrationEventSubscriberRegister,
                environment,
                eventProperties.eventScanPackage,
                svcName,
                baseUrl,
                SUBSCRIBE_PATH,
                CONSUME_PATH
            ).apply {
                init()
            }
        }

        @ConditionalOnWebApplication
        @Bean(name = [SUBSCRIBE_PATH])
        fun httpIntegrationEventSubscribeHandler(
            httpIntegrationEventSubscriberRegister: HttpIntegrationEventSubscriberRegister,
            @Value("\${server.port:80}")
            serverPort: String,
            @Value("\${server.servlet.context-path:}")
            serverServletContentPath: String,
        ): HttpRequestHandler {
            log.info("IntegrationEvent subscribe URL: http://localhost:$serverPort$serverServletContentPath$SUBSCRIBE_PATH?$EVENT_PARAM={event}&$SUBSCRIBER_PARAM={subscriber}")
            return HttpRequestHandler { req, res ->
                val event = req.getParameter(EVENT_PARAM)
                val subscriber = req.getParameter(SUBSCRIBER_PARAM)
                val scanner = Scanner(req.inputStream, StandardCharsets.UTF_8.name())
                val stringBuilder = StringBuilder()
                while (scanner.hasNextLine()) {
                    stringBuilder.append(scanner.nextLine())
                }
                val callbackUrl = JSON.parseObject(stringBuilder.toString(), String::class.java)

                val operationResponse = if (event.isNotBlank() &&
                    subscriber.isNotBlank() &&
                    callbackUrl.isNotBlank()
                ) {
                    val success = httpIntegrationEventSubscriberRegister.subscribe(event, subscriber, callbackUrl)
                    HttpIntegrationEventSubscriberAdapter.OperationResponse<Any>(
                        success = success,
                        message = if (success) "ok" else "fail",
                    )
                } else {
                    HttpIntegrationEventSubscriberAdapter.OperationResponse<Any>(
                        success = false,
                        message = "必要参数缺失"
                    )
                }

                res.apply {
                    characterEncoding = StandardCharsets.UTF_8.name()
                    contentType = "application/json; charset=utf-8"
                    writer.write(JSON.toJSONString(operationResponse))
                    writer.flush()
                    writer.close()
                }
            }
        }

        @ConditionalOnWebApplication
        @Bean(name = [UNSUBSCRIBE_PATH])
        fun httpIntegrationEventUnsubscribeHandler(
            httpIntegrationEventSubscriberRegister: HttpIntegrationEventSubscriberRegister,
            @Value("\${server.port:80}")
            serverPort: String,
            @Value("\${server.servlet.context-path:}")
            serverServletContentPath: String,
        ): HttpRequestHandler {
            log.info("IntegrationEvent unsubscribe URL: http://localhost:$serverPort$serverServletContentPath$UNSUBSCRIBE_PATH?$EVENT_PARAM={event}&$SUBSCRIBER_PARAM={subscriber}")
            return HttpRequestHandler { req, res ->
                val event = req.getParameter(EVENT_PARAM)
                val subscriber = req.getParameter(SUBSCRIBER_PARAM)
                val success = httpIntegrationEventSubscriberRegister.unsubscribe(event, subscriber)
                val operationResponse = HttpIntegrationEventSubscriberAdapter.OperationResponse<Any>(
                    success = success,
                    message = if (success) "ok" else "fail"
                )

                res.apply {
                    characterEncoding = StandardCharsets.UTF_8.name()
                    contentType = "application/json; charset=utf-8"
                    writer.write(JSON.toJSONString(operationResponse))
                    writer.flush()
                    writer.close()
                }
            }
        }

        @ConditionalOnWebApplication
        @Bean(name = [EVENTS_PATH])
        fun httpIntegrationEventEventsHandler(
            httpIntegrationEventSubscriberRegister: HttpIntegrationEventSubscriberRegister,
            @Value("\${server.port:80}")
            serverPort: String,
            @Value("\${server.servlet.context-path:}")
            serverServletContentPath: String,
        ): HttpRequestHandler {
            log.info("IntegrationEvent events URL: http://localhost:$serverPort$serverServletContentPath$EVENTS_PATH")
            return HttpRequestHandler { req, res ->
                val operationResponse = try {
                    val events = httpIntegrationEventSubscriberRegister.events()
                    HttpIntegrationEventSubscriberAdapter.OperationResponse(
                        success = true,
                        message = "ok",
                        data = events
                    )
                } catch (throwable: Throwable) {
                    HttpIntegrationEventSubscriberAdapter.OperationResponse(
                        success = false,
                        message = throwable.message
                    )
                }

                res.apply {
                    characterEncoding = StandardCharsets.UTF_8.name()
                    contentType = "application/json; charset=utf-8"
                    writer.write(JSON.toJSONString(operationResponse))
                    writer.flush()
                    writer.close()
                }
            }
        }

        @ConditionalOnWebApplication
        @Bean(name = [SUBSCRIBERS_PATH])
        fun httpIntegrationEventSubscribersHandler(
            httpIntegrationEventSubscriberRegister: HttpIntegrationEventSubscriberRegister,
            @Value("\${server.port:80}")
            serverPort: String,
            @Value("\${server.servlet.context-path:}")
            serverServletContentPath: String,
        ): HttpRequestHandler {
            log.info("IntegrationEvent subscribers URL: http://localhost:$serverPort$serverServletContentPath$SUBSCRIBERS_PATH?$EVENT_PARAM={event}")
            return HttpRequestHandler { req, res ->
                val event = req.getParameter(EVENT_PARAM)
                val operationResponse = try {
                    val subscribers = httpIntegrationEventSubscriberRegister.subscribers(event)
                    HttpIntegrationEventSubscriberAdapter.OperationResponse(
                        success = true,
                        message = "ok",
                        data = subscribers
                    )
                } catch (throwable: Throwable) {
                    HttpIntegrationEventSubscriberAdapter.OperationResponse(
                        success = false, message = throwable.message
                    )
                }

                res.apply {
                    characterEncoding = StandardCharsets.UTF_8.name()
                    contentType = "application/json; charset=utf-8"
                    writer.write(JSON.toJSONString(operationResponse))
                    writer.flush()
                    writer.close()
                }
            }
        }

        @ConditionalOnWebApplication
        @Bean(name = [CONSUME_PATH])
        fun httpIntegrationEventConsumeHandler(
            httpIntegrationEventSubscriberAdapter: HttpIntegrationEventSubscriberAdapter,
            @Value("\${server.port:80}")
            serverPort: String,
            @Value("\${server.servlet.context-path:}")
            serverServletContentPath: String,
        ): HttpRequestHandler {
            log.info("IntegrationEvent consume URL: http://localhost:$serverPort$serverServletContentPath$CONSUME_PATH?$EVENT_PARAM={event}&$EVENT_ID_PARAM={uuid}")
            return HttpRequestHandler { req, res ->
                val scanner = Scanner(req.inputStream, StandardCharsets.UTF_8.name())
                val stringBuilder = StringBuilder()
                while (scanner.hasNextLine()) {
                    stringBuilder.append(scanner.nextLine())
                }
                val eventId = req.getParameter(EVENT_ID_PARAM)
                val event = req.getParameter(EVENT_PARAM)
                log.info("IntegrationEvent uuid={} event={}", eventId, event)

                val headers = mutableMapOf<String, Any>()
                headers[EVENT_ID_PARAM] = eventId

                try {
                    val headerNames = req.headerNames
                    while (headerNames.hasMoreElements()) {
                        val headerName = headerNames.nextElement()
                        val headerValueEnumeration = req.getHeaders(headerName)
                        if (headerValueEnumeration.hasMoreElements()) {
                            val headerValues = mutableListOf<String>()
                            while (headerValueEnumeration.hasMoreElements()) {
                                headerValues.add(headerValueEnumeration.nextElement())
                            }
                            val headerValue = if (headerValues.size == 1) {
                                headerValues[0]
                            } else {
                                headerValues
                            }
                            headers[headerName] = headerValue
                        }
                    }
                } catch (e: Exception) {
                    log.warn("读取请求头异常", e)
                    /* don't care */
                }

                val success = httpIntegrationEventSubscriberAdapter.consume(
                    event, stringBuilder.toString(), headers
                )
                val operationResponse = HttpIntegrationEventSubscriberAdapter.OperationResponse<Any>(
                    success = success,
                    message = if (success) "ok" else "fail"
                )

                res.apply {
                    characterEncoding = StandardCharsets.UTF_8.name()
                    contentType = "application/json; charset=utf-8"
                    writer.write(JSON.toJSONString(operationResponse))
                    writer.flush()
                    writer.close()
                }
            }
        }
    }

    @Configuration
    @ConditionalOnClass(name = ["com.only4.cap4k.ddd.application.event.RocketMqIntegrationEventSubscriberAdapter"])
    @ImportAutoConfiguration(RocketMQAutoConfiguration::class)
    class RocketMqAdapterLauncher {

        companion object {
            private val log = LoggerFactory.getLogger(RocketMqAdapterLauncher::class.java)
        }

        @Bean
        @ConditionalOnProperty(name = ["rocketmq.name-server"])
        @ConditionalOnMissingBean(IntegrationEventPublisher::class)
        fun rocketMqIntegrationEventPublisher(
            rocketMQTemplate: RocketMQTemplate,
            environment: Environment,
        ): RocketMqIntegrationEventPublisher {
            return RocketMqIntegrationEventPublisher(rocketMQTemplate, environment)
        }

        @Bean
        @ConditionalOnProperty(name = ["rocketmq.name-server"])
        fun rocketMqDomainEventSubscriberAdapter(
            eventSubscriberManager: EventSubscriberManager,
            eventMessageInterceptors: List<EventMessageInterceptor>,
            @Autowired(required = false)
            rocketMqIntegrationEventConfigure: RocketMqIntegrationEventConfigure?,
            environment: Environment,
            eventProperties: EventProperties,
            @Value(CONFIG_KEY_4_SVC_NAME)
            svcName: String,
            @Value(CONFIG_KEY_4_ROCKETMQ_NAME_SERVER)
            defaultNameSrv: String,
            @Value(CONFIG_KEY_4_ROCKETMQ_MSG_CHARSET)
            msgCharset: String,
        ): RocketMqIntegrationEventSubscriberAdapter {
            return RocketMqIntegrationEventSubscriberAdapter(
                eventSubscriberManager,
                eventMessageInterceptors,
                rocketMqIntegrationEventConfigure,
                environment,
                eventProperties.eventScanPackage,
                svcName,
                defaultNameSrv,
                msgCharset
            ).apply {
                init()
                log.info("集成事件适配类型：RocketMQ")
            }
        }
    }

    @Configuration
    @ConditionalOnClass(name = ["com.only4.cap4k.ddd.application.event.RabbitMqIntegrationEventSubscriberAdapter"])
    class RabbitMqAdapterLauncher {

        companion object {
            private val log = LoggerFactory.getLogger(RabbitMqAdapterLauncher::class.java)
        }

        @Bean
        @ConditionalOnProperty(name = ["spring.rabbitmq.host"])
        @ConditionalOnClass(name = ["org.springframework.amqp.rabbit.core.RabbitTemplate"])
        @ConditionalOnMissingBean(IntegrationEventPublisher::class)
        fun rabbitMqIntegrationEventPublisher(
            rabbitTemplate: Any,
            connectionFactory: Any,
            environment: Environment,
            rabbitMqIntegrationEventAdapterProperties: RabbitMqIntegrationEventAdapterProperties,
        ): Any {
            val template = rabbitTemplate as RabbitTemplate
            val factory = connectionFactory as ConnectionFactory

            return RabbitMqIntegrationEventPublisher(
                template,
                factory,
                environment,
                rabbitMqIntegrationEventAdapterProperties.publishThreadPoolSize,
                rabbitMqIntegrationEventAdapterProperties.publishThreadFactoryClassName,
                rabbitMqIntegrationEventAdapterProperties.autoDeclareExchange,
                rabbitMqIntegrationEventAdapterProperties.defaultExchangeType
            ).apply {
                init()
            }
        }

        @Bean
        @ConditionalOnProperty(name = ["spring.rabbitmq.host"])
        @ConditionalOnClass(name = ["org.springframework.amqp.rabbit.connection.ConnectionFactory"])
        fun rabbitMqIntegrationEventSubscriberAdapter(
            eventSubscriberManager: EventSubscriberManager,
            eventMessageInterceptors: List<EventMessageInterceptor>,
            @Autowired(required = false)
            rabbitMqIntegrationEventConfigure: RabbitMqIntegrationEventConfigure?,
            simpleRabbitListenerContainerFactory: Any,
            connectionFactory: Any,
            environment: Environment,
            eventProperties: EventProperties,
            @Value(CONFIG_KEY_4_SVC_NAME)
            svcName: String,
            @Value(CONFIG_KEY_4_ROCKETMQ_MSG_CHARSET)
            msgCharset: String,
            rabbitMqIntegrationEventAdapterProperties: RabbitMqIntegrationEventAdapterProperties,
        ): Any {
            val factory = simpleRabbitListenerContainerFactory as SimpleRabbitListenerContainerFactory
            val connection = connectionFactory as ConnectionFactory

            return RabbitMqIntegrationEventSubscriberAdapter(
                eventSubscriberManager,
                eventMessageInterceptors,
                rabbitMqIntegrationEventConfigure,
                factory,
                connection,
                environment,
                eventProperties.eventScanPackage,
                svcName,
                msgCharset,
                rabbitMqIntegrationEventAdapterProperties.autoDeclareQueue
            ).apply {
                init()
                log.info("集成事件适配类型：RabbitMQ")
            }
        }
    }
}
