package com.only4.cap4k.ddd.application.event

import org.apache.rocketmq.client.consumer.MQPushConsumer

/**
 * 配置领域事件的MQ配置接口
 *
 * @author binking338
 * @date 2024/3/28
 */
interface RocketMqIntegrationEventConfigure {
    /**
     * 获取指定集成事件类的MQ推送消费者
     *
     * @param integrationEventClass 集成事件类
     * @return MQ推送消费者
     */
    fun get(integrationEventClass: Class<*>): MQPushConsumer?
}
