package com.only4.cap4k.ddd.core.domain.repo.impl

import com.only4.cap4k.ddd.core.domain.event.DomainEventSupervisor
import com.only4.cap4k.ddd.core.domain.event.annotation.AutoAttach
import com.only4.cap4k.ddd.core.domain.repo.PersistListener
import com.only4.cap4k.ddd.core.domain.repo.PersistListenerManager
import com.only4.cap4k.ddd.core.domain.repo.PersistType
import com.only4.cap4k.ddd.core.share.misc.findDomainEventClasses
import com.only4.cap4k.ddd.core.share.misc.newConverterInstance
import com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass
import org.springframework.core.Ordered
import org.springframework.core.annotation.OrderUtils
import org.springframework.core.convert.converter.Converter
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * 默认实体持久化监听管理器
 *
 * @author LD_moxeii
 * @date 2025/07/26
 */
class DefaultPersistListenerManager(
    private val persistListeners: List<PersistListener<*>>,
    private val eventClassScanPath: String
) : PersistListenerManager {

    private val persistListenersMap by lazy {
        ConcurrentHashMap<Class<*>, MutableList<PersistListener<*>>>().also { map ->
            initializePersistListeners(map)
        }
    }

    fun init() {
        // 预热persistListenersMap，触发lazy初始化
        persistListenersMap
    }

    private fun initializePersistListeners(map: MutableMap<Class<*>, MutableList<PersistListener<*>>>) {
        // 按照Order排序持久化监听器
        val sortedListeners = persistListeners.sortedBy { listener ->
            OrderUtils.getOrder(listener.javaClass, Ordered.LOWEST_PRECEDENCE)
        }

        // 注册持久化监听器
        sortedListeners.forEach { persistListener ->
            val entityClass = resolveGenericTypeClass(
                persistListener, 0,
                AbstractPersistListener::class.java, PersistListener::class.java
            )
            subscribeInternal(map, entityClass, persistListener)
        }

        // 处理自动附加领域事件的注解
        findDomainEventClasses(eventClassScanPath)
            .filter { it.isAnnotationPresent(AutoAttach::class.java) }
            .forEach { domainEventClass ->
                val autoAttach = domainEventClass.getAnnotation(AutoAttach::class.java)

                val converterClass = when {
                    Converter::class.java.isAssignableFrom(domainEventClass) -> domainEventClass
                    Converter::class.java.isAssignableFrom(autoAttach.converterClass.java) -> autoAttach.converterClass.java
                    else -> null
                }

                val converter = newConverterInstance(
                    autoAttach.sourceEntityClass.java,
                    domainEventClass,
                    converterClass
                )

                subscribeInternal(map, autoAttach.sourceEntityClass.java) { entity, type ->
                    if (type in autoAttach.persistType) {
                        @Suppress("UNCHECKED_CAST")
                        val domainEvent = converter.convert(entity)!!
                        with(entity) {
                            DomainEventSupervisor.instance.attach(
                                domainEvent,
                                Duration.ofSeconds(autoAttach.delayInSeconds.toLong())
                            )
                        }
                        DomainEventSupervisor.manager.release(setOf(entity))
                    }
                }
            }
    }

    /**
     * 订阅持久化事件监听器
     */
    private fun subscribeInternal(
        map: MutableMap<Class<*>, MutableList<PersistListener<*>>>,
        entityClass: Class<*>,
        persistListener: PersistListener<*>
    ) {
        map.computeIfAbsent(entityClass) { mutableListOf() }.add(persistListener)
    }

    /**
     * onCreate & onUpdate & onDelete
     */
    override fun <Entity : Any> onChange(aggregate: Entity, type: PersistType) {
        val aggregateClass = aggregate.javaClass

        // 处理具体类型的监听器
        processListeners(aggregate, type, aggregateClass)

        // 处理通用Object类型的监听器
        if (aggregateClass != Any::class.java) {
            processListeners(aggregate, type, Any::class.java)
        }
    }

    private fun <Entity : Any> processListeners(aggregate: Entity, type: PersistType, listenerClass: Class<*>) {
        val listeners = persistListenersMap[listenerClass] ?: return

        listeners.forEach { listener ->
            try {
                @Suppress("UNCHECKED_CAST")
                (listener as PersistListener<Entity>).onChange(aggregate, type)
            } catch (ex: Exception) {
                @Suppress("UNCHECKED_CAST")
                (listener as PersistListener<Entity>).onException(aggregate, type, ex)
            }
        }
    }
}
