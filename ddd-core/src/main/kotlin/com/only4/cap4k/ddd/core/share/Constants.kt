package com.only4.cap4k.ddd.core.share

/**
 * 常量定义
 * 包含系统配置和消息头相关的常量
 *
 * @author LD_moxeii
 * @date 2025/07/21
 */
object Constants {
    /**
     * 架构版本信息
     */
    const val ARCH_INFO_VERSION = "3.1.0-alpha-3"

    /**
     * 事件ID的消息头键
     */
    const val HEADER_KEY_CAP4K_EVENT_ID = "cap4k-event-id"

    /**
     * 事件类型的消息头键
     */
    const val HEADER_KEY_CAP4K_EVENT_TYPE = "cap4k-event-type"

    /**
     * 领域事件类型值
     */
    const val HEADER_VALUE_CAP4K_EVENT_TYPE_DOMAIN = "domain"

    /**
     * 集成事件类型值
     */
    const val HEADER_VALUE_CAP4K_EVENT_TYPE_INTEGRATION = "integration"

    /**
     * 时间戳的消息头键
     */
    const val HEADER_KEY_CAP4K_TIMESTAMP = "cap4k-timestamp"

    /**
     * 调度时间的消息头键
     */
    const val HEADER_KEY_CAP4K_SCHEDULE = "cap4k-schedule"

    /**
     * 持久化标志的消息头键
     */
    const val HEADER_KEY_CAP4K_PERSIST = "cap4k-persist"

    /**
     * 服务名称配置键
     */
    const val CONFIG_KEY_4_SVC_NAME = "\${spring.application.name:default}"

    /**
     * 服务版本配置键
     */
    const val CONFIG_KEY_4_SVC_VERSION = "\${spring.application.version:unknown}"

    /**
     * RocketMQ命名服务器配置键
     */
    const val CONFIG_KEY_4_ROCKETMQ_NAME_SERVER = "\${rocketmq.name-server:}"

    /**
     * RocketMQ消息字符集配置键
     */
    const val CONFIG_KEY_4_ROCKETMQ_MSG_CHARSET = "\${rocketmq.msg-charset:UTF-8}"
}
