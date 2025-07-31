package com.only4.cap4k.ddd.application.event

import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer

/**
 * 配置领域事件的MQ配置接口
 *
 * @author LD_moxeii
 * @date 2025/07/31
 */
interface RabbitMqIntegrationEventConfigure {
    /**
     * 根据集成事件类获取消息监听器容器
     *
     * @param integrationEventClass 集成事件类
     * @return SimpleMessageListenerContainer 消息监听器容器
     */
    fun get(integrationEventClass: Class<*>): SimpleMessageListenerContainer?
}