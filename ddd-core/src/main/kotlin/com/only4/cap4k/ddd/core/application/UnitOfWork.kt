package com.only4.cap4k.ddd.core.application

/**
 * 工作单元接口
 * 实现UnitOfWork模式，用于管理实体的持久化操作
 * 提供事务管理和实体状态追踪功能
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
interface UnitOfWork {
    /** Whether the current thread is participating in a Command Unit of Work. */
    val active: Boolean

    /**
     * Execute work in the current Unit of Work or create the outer REQUIRED
     * transaction and complete it automatically.
     */
    fun <RESULT> execute(block: () -> RESULT): RESULT

    /**
     * 提交实体持久化意图。
     *
     * 默认意图为 EXISTING。工厂创建的新聚合应显式传入 PersistIntent.CREATE。
     *
     * @param entity 需要持久化的实体对象
     * @param intent 持久化意图
     * @throws IllegalArgumentException 当实体对象无效时
     */
    fun persist(entity: Any, intent: PersistIntent = PersistIntent.EXISTING)

    /**
     * 提交实体删除意图
     * 将实体的删除操作意图添加到工作单元上下文中
     *
     * @param entity 需要删除的实体对象
     * @throws IllegalArgumentException 当实体对象无效时
     */
    fun remove(entity: Any)

    /**
     * Synchronize current persistent state without draining Domain Events or
     * committing the transaction. This is an advanced operation and requires
     * an active Unit of Work.
     */
    fun flush()

    companion object {
        /**
         * 获取工作单元实例
         * 通过UnitOfWorkSupport获取全局唯一的工作单元实例
         *
         * @return 工作单元实例
         */
        @JvmStatic
        val instance: UnitOfWork
            get() = UnitOfWorkSupport.instance
    }
}
