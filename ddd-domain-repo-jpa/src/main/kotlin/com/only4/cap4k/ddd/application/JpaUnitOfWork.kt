package com.only4.cap4k.ddd.application

import com.only4.cap4k.ddd.core.application.AggregateOperation
import com.only4.cap4k.ddd.core.application.UnitOfWork
import com.only4.cap4k.ddd.core.application.UnitOfWorkInterceptor
import com.only4.cap4k.ddd.core.domain.aggregate.Aggregate
import com.only4.cap4k.ddd.core.domain.aggregate.ValueObject
import com.only4.cap4k.ddd.core.domain.repo.PersistListenerManager
import com.only4.cap4k.ddd.core.domain.repo.PersistType
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.hibernate.engine.spi.SessionImplementor
import org.slf4j.LoggerFactory
import org.springframework.data.jpa.repository.support.JpaEntityInformationSupport
import org.springframework.data.repository.core.EntityInformation
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.ConcurrentHashMap

/**
 * 基于Jpa的UnitOfWork实现
 */
open class JpaUnitOfWork(
    private val uowInterceptors: List<UnitOfWorkInterceptor>,
    private val persistListenerManager: PersistListenerManager,
    private val supportEntityInlinePersistListener: Boolean,
    private val supportValueObjectExistsCheckOnSave: Boolean
) : UnitOfWork {

    @PersistenceContext
    lateinit var entityManager: EntityManager
        protected set

    companion object {
        private val logger = LoggerFactory.getLogger(JpaUnitOfWork::class.java)
        lateinit var instance: JpaUnitOfWork
            private set

        /**
         * 修复AOP包装
         */
        @JvmStatic
        fun fixAopWrapper(unitOfWork: JpaUnitOfWork) {
            instance = unitOfWork
        }

        private val persistEntitiesThreadLocal = ThreadLocal.withInitial { LinkedHashSet<Any>() }
        private val removeEntitiesThreadLocal = ThreadLocal.withInitial { LinkedHashSet<Any>() }
        private val processingEntitiesThreadLocal = ThreadLocal.withInitial { LinkedHashSet<Any>() }
        private val wrapperMapThreadLocal = ThreadLocal.withInitial { HashMap<Any, Aggregate<*>>() }

        private val entityInformationCache = ConcurrentHashMap<Class<*>, EntityInformation<*, *>>()

        /**
         * 重置线程本地变量
         */
        @JvmStatic
        fun reset() {
            persistEntitiesThreadLocal.remove()
            removeEntitiesThreadLocal.remove()
            processingEntitiesThreadLocal.remove()
            wrapperMapThreadLocal.remove()
        }
    }

    /**
     * 获取实体信息
     */
    private fun getEntityInformation(entityClass: Class<*>): EntityInformation<*, *> {
        return entityInformationCache.computeIfAbsent(entityClass) { cls ->
            JpaEntityInformationSupport.getEntityInformation(cls, entityManager)
        }
    }

    /**
     * 检查值对象是否存在
     */
    private fun isValueObjectAndExists(entity: Any): Boolean {
        val valueObject = if (entity is ValueObject<*>) entity else null
        return if (valueObject != null) {
            val id = valueObject.hash()
            entityManager.find(entity.javaClass, id) != null
        } else {
            false
        }
    }

    /**
     * 检查实体是否存在
     */
    private fun isExists(entity: Any): Boolean {
        val entityClass = entity.javaClass
        val entityInformation = getEntityInformation(entityClass)
        val isValueObject = entity is ValueObject<*>


        if (!isValueObject) {
            // 类型安全转换
            @Suppress("UNCHECKED_CAST")
            val typedInfo = entityInformation as EntityInformation<in Any, *>
            if (typedInfo.isNew(entity)) {
                return false
            }

            val id = typedInfo.getId(entity)
            return id != null && entityManager.find(entityClass, id) != null
        } else {
            val valueObject = entity as ValueObject<*>
            val id = valueObject.hash()
            return entityManager.find(entityClass, id) != null
        }
    }

    /**
     * 获取持久化上下文中的实体
     */
    protected fun persistenceContextEntities(): List<Any> {
        return try {
            val delegate = entityManager.delegate
            if (delegate is SessionImplementor && !delegate.isClosed) {
                val persistenceContext = delegate.persistenceContext
                persistenceContext.reentrantSafeEntityEntries()
                    .map { it.key }
                    .toList()
            } else {
                emptyList()
            }
        } catch (ex: Exception) {
            logger.debug("跟踪实体获取失败", ex)
            emptyList()
        }
    }

    /**
     * 实体刷新时触发事件
     */
    protected fun onEntitiesFlushed(
        createdEntities: Set<Any>,
        updatedEntities: Set<Any>,
        deletedEntities: Set<Any>
    ) {
        if (!supportEntityInlinePersistListener) {
            return
        }

        createdEntities.forEach { entity ->
            persistListenerManager.onChange(entity, PersistType.CREATE)
        }

        updatedEntities.forEach { entity ->
            persistListenerManager.onChange(entity, PersistType.UPDATE)
        }

        deletedEntities.forEach { entity ->
            persistListenerManager.onChange(entity, PersistType.DELETE)
        }
    }

    /**
     * 解包实体
     */
    private fun unwrapEntity(entity: Any): Any {
        if (entity !is Aggregate<*>) {
            return entity
        }

        val unwrappedEntity = entity._unwrap()
        wrapperMapThreadLocal.get()[unwrappedEntity] = entity
        return unwrappedEntity
    }

    /**
     * 更新包装实体
     */
    private fun updateWrappedEntity(entity: Any, updatedEntity: Any) {
        val wrapperMap = wrapperMapThreadLocal.get()
        if (!wrapperMap.containsKey(entity)) {
            return
        }

        val aggregate = wrapperMap.remove(entity)!!
        @Suppress("UNCHECKED_CAST")
        (aggregate as Aggregate<in Any>)._wrap(updatedEntity)
        wrapperMap[updatedEntity] = aggregate
    }

    /**
     * 持久化实体
     */
    override fun persist(entity: Any) {
        val unwrappedEntity = unwrapEntity(entity)
        if (isValueObjectAndExists(unwrappedEntity)) {
            return
        }
        persistEntitiesThreadLocal.get().add(unwrappedEntity)
    }

    /**
     * 持久化实体（如果不存在）
     */
    override fun persistIfNotExist(entity: Any): Boolean {
        val unwrappedEntity = unwrapEntity(entity)
        if (isExists(unwrappedEntity)) {
            return false
        }
        persistEntitiesThreadLocal.get().add(unwrappedEntity)
        return true
    }

    /**
     * 移除实体
     */
    override fun remove(entity: Any) {
        val unwrappedEntity = unwrapEntity(entity)
        removeEntitiesThreadLocal.get().add(unwrappedEntity)
    }


    /**
     * 添加到处理中的实体
     */
    private fun pushProcessingEntities(
        entity: Any,
        currentProcessedPersistenceContextEntities: MutableSet<Any>
    ): Boolean {
        val processingEntities = processingEntitiesThreadLocal.get()
        if (entity !in processingEntities) {
            processingEntities.add(entity)
            currentProcessedPersistenceContextEntities.add(entity)
            return true
        }

        return false
    }

    /**
     * 从处理中的实体移除
     */
    private fun popProcessingEntities(currentProcessedPersistenceContextEntities: Set<Any>): Boolean {
        return if (currentProcessedPersistenceContextEntities.isNotEmpty()) {
            processingEntitiesThreadLocal.get().removeAll(currentProcessedPersistenceContextEntities)
        } else {
            true
        }
    }

    /**
     * 保存（指定传播方式）
     */
    override fun save(propagation: Propagation) {
        val currentProcessedEntitySet = LinkedHashSet<Any>()

        val persistEntitySet = persistEntitiesThreadLocal.get()
        if (persistEntitySet.isNotEmpty()) {
            persistEntitiesThreadLocal.remove()
            persistEntitySet.forEach { e -> pushProcessingEntities(e, currentProcessedEntitySet) }
        }

        val deleteEntitySet = removeEntitiesThreadLocal.get()
        if (deleteEntitySet.isNotEmpty()) {
            removeEntitiesThreadLocal.remove()
            deleteEntitySet.forEach { e -> pushProcessingEntities(e, currentProcessedEntitySet) }
        }

        // 事务前拦截器
        uowInterceptors.forEach { interceptor ->
            interceptor.beforeTransaction(AggregateOperation(persistEntitySet, deleteEntitySet))
        }

        val saveAndDeleteEntities = arrayOf(
            persistEntitySet,
            deleteEntitySet,
            currentProcessedEntitySet
        )

        // 执行保存
        save(
            transactionHandler = { input ->
                val persistEntities = input[0] as Set<Any>
                val deleteEntities = input[1] as Set<Any>
                val processedEntities = input[2] as MutableSet<Any>

                val createdEntities = LinkedHashSet<Any>()
                val updatedEntities = LinkedHashSet<Any>()
                val deletedEntities = LinkedHashSet<Any>()

                // 事务内前置拦截器
                uowInterceptors.forEach { interceptor ->
                    interceptor.preInTransaction(AggregateOperation(persistEntities, deleteEntities))
                }

                var flush = false
                val refreshEntityList: MutableList<Any> = ArrayList()

                // 处理持久化实体
                if (persistEntities.isNotEmpty()) {
                    flush = true
                    for (entity in persistEntities) {
                        if (supportValueObjectExistsCheckOnSave && entity is ValueObject<*>) {
                            if (!isExists(entity)) {
                                entityManager.persist(entity)
                                createdEntities.add(entity)
                            }
                            continue
                        }

                        val entityInformation = getEntityInformation(entity.javaClass) as EntityInformation<in Any, *>
                        if (entityInformation.isNew(entity)) {
                            if (!entityManager.contains(entity)) {
                                entityManager.persist(entity)
                            }
                            refreshEntityList.add(entity)
                            createdEntities.add(entity)
                        } else {
                            if (!entityManager.contains(entity)) {
                                val mergedEntity = entityManager.merge(entity)
                                updateWrappedEntity(entity, mergedEntity)
                            }
                            updatedEntities.add(entity)
                        }
                    }
                }

                // 处理删除实体
                if (deleteEntities.isNotEmpty()) {
                    flush = true
                    for (entity in deleteEntities) {
                        if (entityManager.contains(entity)) {
                            entityManager.remove(entity)
                        } else {
                            val mergedEntity = entityManager.merge(entity)
                            updateWrappedEntity(entity, mergedEntity)
                            entityManager.remove(mergedEntity)
                        }
                        deletedEntities.add(entity)
                    }
                }

                // 刷新实体
                if (flush) {
                    entityManager.flush()
                    refreshEntityList.forEach { entity ->
                        entityManager.refresh(entity)
                    }
                    onEntitiesFlushed(createdEntities, updatedEntities, deletedEntities)
                }

                // 收集所有处理的实体
                val entities = LinkedHashSet<Any>()
                entities.addAll(persistEntities)
                entities.addAll(deleteEntities)

                persistenceContextEntities().forEach { entity ->
                    pushProcessingEntities(entity, processedEntities)
                }
                entities.addAll(processedEntities)

                // 实体持久化后拦截器
                uowInterceptors.forEach { interceptor ->
                    interceptor.postEntitiesPersisted(entities)
                }

                // 事务内后置拦截器
                uowInterceptors.forEach { interceptor ->
                    interceptor.postInTransaction(AggregateOperation(persistEntities, deleteEntities))
                }
            },
            input = saveAndDeleteEntities,
            propagation = propagation
        )

        // 事务后拦截器
        uowInterceptors.forEach { interceptor ->
            interceptor.afterTransaction(AggregateOperation(persistEntitySet, deleteEntitySet))
        }

        popProcessingEntities(currentProcessedEntitySet)
    }

    /**
     * 事务执行处理器
     */
    fun interface TransactionHandler<I, O> {
        fun exec(input: I): O
    }

    /**
     * 在事务中保存
     */
    fun <I, O> save(transactionHandler: TransactionHandler<I, O>, input: I, propagation: Propagation): O {
        return when (propagation) {
            Propagation.SUPPORTS -> instance.supports(transactionHandler, input)
            Propagation.NOT_SUPPORTED -> instance.notSupported(transactionHandler, input)
            Propagation.REQUIRES_NEW -> instance.requiresNew(transactionHandler, input)
            Propagation.MANDATORY -> instance.mandatory(transactionHandler, input)
            Propagation.NEVER -> instance.never(transactionHandler, input)
            Propagation.NESTED -> instance.nested(transactionHandler, input)
            else -> instance.required(transactionHandler, input)
        }
    }

    /**
     * 在REQUIRED事务中执行
     */
    @Transactional(rollbackFor = [Exception::class], propagation = Propagation.REQUIRED)
    open fun <I, O> required(transactionHandler: TransactionHandler<I, O>, input: I): O {
        return transactionWrapper(transactionHandler, input)
    }

    /**
     * 在REQUIRES_NEW事务中执行
     */
    @Transactional(rollbackFor = [Exception::class], propagation = Propagation.REQUIRES_NEW)
    open fun <I, O> requiresNew(transactionHandler: TransactionHandler<I, O>, input: I): O {
        return transactionWrapper(transactionHandler, input)
    }

    /**
     * 在SUPPORTS事务中执行
     */
    @Transactional(rollbackFor = [Exception::class], propagation = Propagation.SUPPORTS)
    open fun <I, O> supports(transactionHandler: TransactionHandler<I, O>, input: I): O {
        return transactionWrapper(transactionHandler, input)
    }

    /**
     * 在NOT_SUPPORTED事务中执行
     */
    @Transactional(rollbackFor = [Exception::class], propagation = Propagation.NOT_SUPPORTED)
    open fun <I, O> notSupported(transactionHandler: TransactionHandler<I, O>, input: I): O {
        return transactionWrapper(transactionHandler, input)
    }

    /**
     * 在MANDATORY事务中执行
     */
    @Transactional(rollbackFor = [Exception::class], propagation = Propagation.MANDATORY)
    open fun <I, O> mandatory(transactionHandler: TransactionHandler<I, O>, input: I): O {
        return transactionWrapper(transactionHandler, input)
    }

    /**
     * 在NEVER事务中执行
     */
    @Transactional(rollbackFor = [Exception::class], propagation = Propagation.NEVER)
    open fun <I, O> never(transactionHandler: TransactionHandler<I, O>, input: I): O {
        return transactionWrapper(transactionHandler, input)
    }

    /**
     * 在NESTED事务中执行
     */
    @Transactional(rollbackFor = [Exception::class], propagation = Propagation.NESTED)
    open fun <I, O> nested(transactionHandler: TransactionHandler<I, O>, input: I): O {
        return transactionWrapper(transactionHandler, input)
    }

    /**
     * 事务执行包装器
     */
    protected fun <I, O> transactionWrapper(transactionHandler: TransactionHandler<I, O>, input: I): O {
        return transactionHandler.exec(input)
    }
}
