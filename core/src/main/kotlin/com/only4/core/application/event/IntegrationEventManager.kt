package com.only4.core.application.event

/**
 * 集成事件管理器接口
 * 负责管理集成事件的发布和生命周期
 *
 * @author binking338
 * @date 2024/9/11
 */
interface IntegrationEventManager {
    /**
     * 发布所有附加到持久化上下文的集成事件
     * 在事务提交后调用此方法，确保事件被正确发布
     */
    fun release()
}
