package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.context.ExecutionContextBoundary
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextScopeManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventDeliveryMetadata
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelopeCodec
import com.only4.cap4k.ddd.core.application.event.IntegrationEventRouteResolver
import com.only4.cap4k.ddd.core.application.event.deliveryContext
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderState
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderStateReporter
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptor
import com.only4.cap4k.ddd.core.domain.event.InboundIntegrationEventRegistrationView
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContext
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContextScopeManager
import com.only4.cap4k.ddd.core.domain.event.ReliableEventRedeliveryHint
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus
import org.apache.rocketmq.common.consumer.ConsumeFromWhere
import org.apache.rocketmq.common.message.MessageExt
import org.apache.rocketmq.remoting.exception.RemotingConnectException
import org.apache.rocketmq.remoting.exception.RemotingException
import org.apache.rocketmq.remoting.exception.RemotingSendRequestException
import org.apache.rocketmq.remoting.exception.RemotingTimeoutException
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.OrderUtils
import org.springframework.messaging.support.GenericMessage
import java.io.EOFException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeoutException

data class RocketMqConsumerSubscription(
    val eventName: String,
    val payloadType: Class<*>,
    val route: RocketMqIntegrationEventRoute,
    val consumerGroup: String,
)

fun interface RocketMqConsumerFactory {
    fun create(subscription: RocketMqConsumerSubscription): DefaultMQPushConsumer
}

interface RocketMqRecoveryScheduler : AutoCloseable {
    fun schedule(delay: Duration, task: () -> Unit): AutoCloseable

    override fun close() = Unit
}

/** Default bounded scheduler used only for initial RocketMQ subscription recovery. */
class ScheduledRocketMqRecoveryScheduler : RocketMqRecoveryScheduler {
    private val monitor = Any()
    private var executor: ScheduledExecutorService? = null
    private var closed = false

    override fun schedule(delay: Duration, task: () -> Unit): AutoCloseable {
        require(!delay.isNegative && !delay.isZero) { "RocketMQ recovery interval must be positive" }
        val scheduler = synchronized(monitor) {
            check(!closed) { "RocketMQ recovery scheduler is closed" }
            executor ?: Executors.newSingleThreadScheduledExecutor(DaemonThreadFactory).also { executor = it }
        }
        val future = scheduler.schedule(task, delay.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
        return AutoCloseable { future.cancel(false) }
    }

    override fun close() {
        val scheduler = synchronized(monitor) {
            if (closed) return
            closed = true
            executor.also { executor = null }
        }
        scheduler?.shutdownNow()
    }

    private object DaemonThreadFactory : ThreadFactory {
        override fun newThread(runnable: Runnable): Thread = Thread(runnable, "cap4k-rocketmq-recovery").apply {
            isDaemon = true
        }
    }
}

/** RocketMQ inbound adapter with explicit subscription enrollment and recovery. */
class RocketMqIntegrationEventSubscriberAdapter(
    private val eventHandlerDispatcher: EventHandlerDispatcher,
    private val eventMessageInterceptors: List<EventMessageInterceptor>,
    private val routeResolver: IntegrationEventRouteResolver<RocketMqIntegrationEventRoute>,
    private val consumerGroupResolver: RocketMqConsumerGroupResolver,
    private val eventTypeCatalog: InboundIntegrationEventRegistrationView,
    private val applicationName: String,
    private val defaultNameSrv: String,
    private val msgCharset: String,
    private val stateReporter: RuntimeProviderStateReporter,
    private val recoveryInterval: Duration = Duration.ofSeconds(5),
    private val executionContextCodecRegistry: ExecutionContextCodecRegistry = ExecutionContextCodecRegistry(emptyList()),
    private val executionContextScopeManager: ExecutionContextScopeManager = ExecutionContextScopeManager {
        AutoCloseable { }
    },
    private val reliableEventDeliveryContextScopeManager: ReliableEventDeliveryContextScopeManager,
    private val envelopeCodec: IntegrationEventEnvelopeCodec = IntegrationEventEnvelopeCodec(),
    private val consumerFactory: RocketMqConsumerFactory? = null,
    private val recoveryScheduler: RocketMqRecoveryScheduler = ScheduledRocketMqRecoveryScheduler(),
) {
    private data class SubscriptionState(
        val subscription: RocketMqConsumerSubscription,
        var consumer: DefaultMQPushConsumer? = null,
        var starting: Boolean = false,
    )

    private class RecoveryRegistration {
        var handle: AutoCloseable? = null
    }

    private val lifecycleMonitor = Any()
    private val subscriptionStates = linkedMapOf<String, SubscriptionState>()
    private var initialized = false
    private var closed = false
    private var recoveryRegistration: RecoveryRegistration? = null

    private val orderedEventMessageInterceptors by lazy {
        eventMessageInterceptors.sortedBy { interceptor ->
            OrderUtils.getOrder(interceptor.javaClass, Ordered.LOWEST_PRECEDENCE)
        }
    }

    fun init() {
        val subscriptions = synchronized(lifecycleMonitor) {
            check(!closed) { "RocketMQ Integration Event subscriber is shut down" }
            if (initialized) return
            require(applicationName.isNotBlank()) { "RocketMQ application name must not be blank" }
            require(!recoveryInterval.isNegative && !recoveryInterval.isZero) {
                "RocketMQ listener recovery interval must be positive"
            }
            val materialized = eventTypeCatalog.integrationEventTypesByName()
                .map { (eventName, payloadType) ->
                    RocketMqConsumerSubscription(
                        eventName = eventName,
                        payloadType = payloadType,
                        route = routeResolver.resolve(eventName),
                        consumerGroup = consumerGroupResolver.resolve(applicationName, eventName),
                    )
                }
            materialized.forEach { subscription ->
                subscriptionStates[subscription.eventName] = SubscriptionState(subscription)
            }
            initialized = true
            materialized
        }

        reportSafely(RuntimeProviderState.RECOVERING, "subscriber-enrolled")
        if (subscriptions.isEmpty()) {
            reportSafely(RuntimeProviderState.HEALTHY, "no-inbound-subscriptions")
            return
        }

        try {
            subscriptions.forEach(::startIfPending)
            scheduleRecoveryIfNeeded()
        } catch (failure: Throwable) {
            shutdown()
            throw failure
        }
    }

    fun shutdown() {
        val (consumers, recovery) = synchronized(lifecycleMonitor) {
            if (closed) return
            closed = true
            val active = subscriptionStates.values.mapNotNull { state ->
                state.consumer?.let { consumer -> state.subscription to consumer }
            }
            subscriptionStates.values.forEach { it.consumer = null }
            val scheduled = recoveryRegistration?.handle
            recoveryRegistration = null
            active to scheduled
        }

        recovery?.runCatching { close() }
        consumers.forEach { (subscription, consumer) ->
            shutdownConsumer(consumer, subscription.eventName)
        }
        runCatching { recoveryScheduler.close() }
            .onFailure { failure ->
                log.warn(
                    "RocketMQ Integration Event recovery scheduler shutdown failed: exceptionType={}",
                    failure::class.java.name,
                )
            }
    }

    private fun startIfPending(subscription: RocketMqConsumerSubscription) {
        val state = synchronized(lifecycleMonitor) {
            if (closed) return
            val current = subscriptionStates[subscription.eventName] ?: return
            if (current.consumer != null || current.starting) return
            current.starting = true
            current
        }

        val consumer = try {
            createConsumer(subscription)
        } catch (failure: Throwable) {
            synchronized(lifecycleMonitor) { state.starting = false }
            if (!RocketMqFailureClassifier.isTemporaryUnavailability(failure)) throw failure
            reportStartFailure(subscription, failure)
            return
        }

        try {
            consumer.registerMessageListener { msgs: List<MessageExt>, context: ConsumeConcurrentlyContext ->
                onMessage(subscription.payloadType, msgs, context)
            }
            consumer.start()
            val installed = synchronized(lifecycleMonitor) {
                state.starting = false
                if (closed) false else true.also { state.consumer = consumer }
            }
            if (!installed) {
                shutdownConsumer(consumer, subscription.eventName)
                return
            }
            reportSubscriberHealth()
        } catch (failure: Throwable) {
            synchronized(lifecycleMonitor) { state.starting = false }
            shutdownConsumer(consumer, subscription.eventName)
            if (!RocketMqFailureClassifier.isTemporaryUnavailability(failure)) throw failure
            reportStartFailure(subscription, failure)
        }
    }

    private fun reportStartFailure(subscription: RocketMqConsumerSubscription, failure: Throwable) {
        reportSafely(RuntimeProviderState.DEGRADED, "consumer-start-failed")
        log.warn(
            "RocketMQ Integration Event consumer start deferred: eventName={}, exceptionType={}",
            subscription.eventName,
            failure::class.java.name,
        )
    }

    private fun scheduleRecoveryIfNeeded() {
        val registration = synchronized(lifecycleMonitor) {
            if (closed || subscriptionStates.values.none { it.consumer == null } || recoveryRegistration != null) {
                return
            }
            RecoveryRegistration().also { recoveryRegistration = it }
        }

        reportSafely(RuntimeProviderState.RECOVERING, "consumer-recovery-scheduled")
        try {
            val handle = recoveryScheduler.schedule(recoveryInterval) {
                synchronized(lifecycleMonitor) {
                    if (closed || recoveryRegistration !== registration) return@schedule
                    recoveryRegistration = null
                }
                try {
                    recoverPending()
                } catch (failure: Throwable) {
                    reportSafely(RuntimeProviderState.DEGRADED, "consumer-recovery-failed")
                    log.warn(
                        "RocketMQ Integration Event consumer recovery stopped: category={}, exceptionType={}",
                        "consumer-recovery-failed",
                        failure::class.java.name,
                    )
                }
            }
            synchronized(lifecycleMonitor) {
                if (closed || recoveryRegistration !== registration) {
                    handle.close()
                } else {
                    registration.handle = handle
                }
            }
        } catch (failure: Throwable) {
            synchronized(lifecycleMonitor) {
                if (recoveryRegistration === registration) recoveryRegistration = null
            }
            reportSafely(RuntimeProviderState.DEGRADED, "consumer-recovery-schedule-failed")
            log.warn(
                "RocketMQ Integration Event consumer recovery scheduling failed: exceptionType={}",
                failure::class.java.name,
            )
        }
    }

    private fun recoverPending() {
        reportSafely(RuntimeProviderState.RECOVERING, "consumer-recovery-attempt")
        val pending = synchronized(lifecycleMonitor) {
            if (closed) return
            subscriptionStates.values
                .filter { it.consumer == null }
                .map { it.subscription }
        }
        pending.forEach(::startIfPending)
        reportSubscriberHealth()
        scheduleRecoveryIfNeeded()
    }

    private fun reportSubscriberHealth() {
        val allHealthy = synchronized(lifecycleMonitor) {
            !closed && subscriptionStates.values.all { it.consumer != null }
        }
        if (allHealthy) reportSafely(RuntimeProviderState.HEALTHY, "consumer-enrollment")
        else if (!closed) reportSafely(RuntimeProviderState.RECOVERING, "consumer-recovery-pending")
    }

    private fun createConsumer(subscription: RocketMqConsumerSubscription): DefaultMQPushConsumer =
        consumerFactory?.create(subscription) ?: DefaultMQPushConsumer(subscription.consumerGroup).apply {
            consumeFromWhere = ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET
            instanceName = applicationName
            if (defaultNameSrv.isNotBlank()) namesrvAddr = defaultNameSrv
            unitName = subscription.payloadType.simpleName
            subscribe(subscription.route.topic, subscription.route.tag)
        }

    @Suppress("UNUSED_PARAMETER")
    private fun onMessage(
        integrationEventClass: Class<*>,
        msgs: List<MessageExt>,
        context: ConsumeConcurrentlyContext,
    ): ConsumeConcurrentlyStatus {
        var eventName: String? = null
        return runCatching {
            msgs.forEach { msg ->
                val envelope = envelopeCodec.decode(String(msg.body, charset(msgCharset)))
                eventName = envelope.eventType
                val eventPayload = envelopeCodec.payloadJson(envelope, integrationEventClass)
                val deliveryContext = envelope.deliveryContext(
                    IntegrationEventDeliveryMetadata(
                        providerDeliveryAttempt = msg.reconsumeTimes + 1,
                        redeliveryHint = if (msg.reconsumeTimes == 0) {
                            ReliableEventRedeliveryHint.FIRST
                        } else {
                            ReliableEventRedeliveryHint.REDELIVERED
                        },
                    )
                )
                val executionContext = executionContextCodecRegistry.decodeExternal(
                    envelope.executionContext,
                    ExecutionContextBoundary.INTEGRATION_EVENT,
                )
                executionContextScopeManager.install(executionContext).use {
                    if (orderedEventMessageInterceptors.isEmpty()) {
                        dispatch(eventPayload, deliveryContext)
                    } else {
                        processWithInterceptors(msg, eventPayload, deliveryContext)
                    }
                }
            }
            reportSubscriberHealth()
            ConsumeConcurrentlyStatus.CONSUME_SUCCESS
        }.getOrElse { failure ->
            reportSafely(RuntimeProviderState.DEGRADED, "consumer-delivery-failed")
            log.warn(
                "RocketMQ Integration Event consume failed: eventName={}, category={}, exceptionType={}",
                eventName ?: "unknown",
                "consumer-delivery-failed",
                failure::class.java.name,
            )
            ConsumeConcurrentlyStatus.RECONSUME_LATER
        }
    }

    private fun processWithInterceptors(
        msg: MessageExt,
        eventPayload: Any,
        deliveryContext: ReliableEventDeliveryContext,
    ) {
        val message = GenericMessage(
            eventPayload,
            EventMessageInterceptor.ModifiableMessageHeaders(msg.properties.toMutableMap()),
        )
        reliableEventDeliveryContextScopeManager.suppress().use {
            orderedEventMessageInterceptors.forEach { it.preSubscribe(message) }
        }
        dispatch(message.payload, deliveryContext)
        reliableEventDeliveryContextScopeManager.suppress().use {
            orderedEventMessageInterceptors.forEach { it.postSubscribe(message) }
        }
    }

    private fun dispatch(eventPayload: Any, deliveryContext: ReliableEventDeliveryContext) {
        reliableEventDeliveryContextScopeManager.install(deliveryContext).use {
            eventHandlerDispatcher.dispatch(eventPayload)
        }
    }

    private fun shutdownConsumer(consumer: DefaultMQPushConsumer, eventName: String?) {
        runCatching { consumer.shutdown() }
            .onFailure { failure ->
                log.warn(
                    "RocketMQ Integration Event consumer shutdown failed: eventName={}, exceptionType={}",
                    eventName ?: "unknown",
                    failure::class.java.name,
                )
            }
    }

    private fun reportSafely(state: RuntimeProviderState, category: String) {
        runCatching { stateReporter.report(state, category) }
            .onFailure { failure ->
                log.warn(
                    "RocketMQ Integration Event provider state report failed: providerId={}, state={}, category={}, exceptionType={}",
                    RocketMqIntegrationEventPublisher.PROVIDER_IDENTITY,
                    state,
                    category,
                    failure::class.java.name,
                )
            }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(RocketMqIntegrationEventSubscriberAdapter::class.java)
    }
}

internal object RocketMqFailureClassifier {
    fun isTemporaryUnavailability(failure: Throwable): Boolean = generateSequence(failure) { it.cause }
        .any { cause ->
            cause is RemotingConnectException ||
                cause is RemotingTimeoutException ||
                cause is RemotingSendRequestException ||
                cause is RemotingException ||
                cause is ConnectException ||
                cause is NoRouteToHostException ||
                cause is SocketException ||
                cause is SocketTimeoutException ||
                cause is UnknownHostException ||
                cause is EOFException ||
                cause is TimeoutException
        }
}
