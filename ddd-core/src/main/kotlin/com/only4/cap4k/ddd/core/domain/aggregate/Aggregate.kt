package com.only4.cap4k.ddd.core.domain.aggregate

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.domain.event.DomainEventSupervisorSupport.events
import java.util.function.Supplier

/**
 * 聚合封装
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
interface Aggregate<ENTITY : Any> {
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

    open class Default<ENTITY : Any>(payload: Any? = null) : Aggregate<ENTITY> {
        protected lateinit var root: ENTITY

        init {
            if (payload != null) {
                require(payload is AggregatePayload<*>) { "payload must be AggregatePayload" }
                @Suppress("UNCHECKED_CAST")
                val root = Mediator.factories.create(payload as AggregatePayload<ENTITY>)
                _wrap(root)
            }
        }

        /**
         * 获取ORM实体
         * 仅供框架调用使用，勿在业务逻辑代码中使用
         * @return
         */
        final override fun _unwrap(): ENTITY {
            return this.root
        }

        /**
         * 封装ORM实体
         * 仅供框架调用使用，勿在业务逻辑代码中使用
         * @param root
         */
        final override fun _wrap(root: ENTITY) {
            this.root = root
        }


        /**
         * 注册领域事件到持久化上下文
         *
         * @param event
         */
        protected open fun registerDomainEvent(event: Any) {
            events().attach(domainEventPayload = event, entity = this)

        }

        /**
         * 注册领域事件到持久化上下文
         *
         * @param eventSupplier
         */
        protected open fun registerDomainEvent(eventSupplier: Supplier<*>) {
            events().attach(eventSupplier, this.root)
        }

        /**
         * 从当前持久化上下文中取消领域事件
         *
         * @param event
         */
        protected open fun cancelDomainEvent(event: Any) {
            events().detach(event, this)
        }
    }
}
