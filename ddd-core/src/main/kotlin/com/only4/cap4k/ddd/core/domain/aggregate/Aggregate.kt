package com.only4.cap4k.ddd.core.domain.aggregate

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.domain.event.DomainEventSupervisorSupport.events

/**
 * 聚合封装
 *
 * @author binking338
 * @date 2025/1/9
 */
interface Aggregate<ENTITY> {
    /**
     * 获取ORM实体
     * 仅供框架调用使用，勿在业务逻辑代码中使用
     * @return
     */
    fun _unwrap(): ENTITY

    /**
     * 封装ORM实体
     * 仅供框架调用使用，勿在业务逻辑代码中使用
     * @param root
     */
    fun _wrap(root: ENTITY)

    class Default<ENTITY>(payload: AggregatePayload<ENTITY>) : Aggregate<ENTITY> {
        protected var root: ENTITY = TODO()

        init {
            val root = Mediator.factories().create(payload)
            _wrap(root)
        }

        /**
         * 获取ORM实体
         * 仅供框架调用使用，勿在业务逻辑代码中使用
         * @return
         */
        override fun _unwrap(): ENTITY {
            return this.root
        }

        /**
         * 封装ORM实体
         * 仅供框架调用使用，勿在业务逻辑代码中使用
         * @param root
         */
        override fun _wrap(root: ENTITY) {
            this.root = root
        }


        /**
         * 注册领域事件到持久化上下文
         *
         * @param event
         */
        protected fun registerDomainEvent(event: Any) {
            events().attach(event, this)
        }

        /**
         * 从当前持久化上下文中取消领域事件
         *
         * @param event
         */
        protected fun cancelDomainEvent(event: Any) {
            events().detach(event, this)
        }
    }
}
