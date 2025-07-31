package com.only4.cap4k.ddd.application.event

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.serializer.SerializerFeature.IgnoreNonFieldGetter
import com.alibaba.fastjson.serializer.SerializerFeature.SkipTransientField
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.share.DomainException
import com.only4.cap4k.ddd.core.share.misc.resolvePlaceholderWithCache
import org.apache.rocketmq.client.producer.SendCallback
import org.apache.rocketmq.client.producer.SendResult
import org.apache.rocketmq.spring.core.RocketMQTemplate
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment

/**
 * 基于RocketMq的集成事件发布器
 * 如下配置需配置好，保障RocketMqTemplate被初始化
 * ## rocketmq
 * #rocketmq.name-server = myrocket.nameserver:9876
 * #rocketmq.producer.group=${spring.application.name}
 *
 * @author binking338
 * @date 2023/8/13
 */
class RocketMqIntegrationEventPublisher(
    private val rocketMQTemplate: RocketMQTemplate,
    private val environment: Environment
) : IntegrationEventPublisher {

    companion object {
        private val log = LoggerFactory.getLogger(RocketMqIntegrationEventPublisher::class.java)
    }

    override fun publish(event: EventRecord, publishCallback: IntegrationEventPublisher.PublishCallback) {
        try {
            var destination = event.type
            destination = resolvePlaceholderWithCache(destination, environment)

            if (destination.isEmpty()) {
                throw DomainException("集成事件发布失败: ${event.id} 缺失topic")
            }

            // MQ消息通道
            rocketMQTemplate.asyncSend(
                destination,
                event.message,
                IntegrationEventSendCallback(event, publishCallback)
            )
        } catch (ex: Exception) {
            log.error("集成事件发布失败: ${event.id}", ex)
        }
    }

    /**
     * 集成事件发送回调
     */
    class IntegrationEventSendCallback(
        private val event: EventRecord,
        private val publishCallback: IntegrationEventPublisher.PublishCallback
    ) : SendCallback {

        companion object {
            private val log = LoggerFactory.getLogger(IntegrationEventSendCallback::class.java)
        }

        override fun onSuccess(sendResult: SendResult) {
            try {
                log.info("集成事件发送成功, ${event.id} msgId=${sendResult.msgId}")
                publishCallback.onSuccess(event)
            } catch (throwable: Throwable) {
                log.error("回调失败（事件发送成功）", throwable)
                publishCallback.onException(event, throwable)
            }
        }

        override fun onException(throwable: Throwable) {
            try {
                val msg = "集成事件发送失败, ${event.id} body=${
                    JSON.toJSONString(event.payload, IgnoreNonFieldGetter, SkipTransientField)
                }"
                log.error(msg, throwable)
                publishCallback.onException(event, throwable)
            } catch (throwable1: Throwable) {
                log.error("回调失败（事件发送异常）", throwable1)
            }
        }
    }
}
