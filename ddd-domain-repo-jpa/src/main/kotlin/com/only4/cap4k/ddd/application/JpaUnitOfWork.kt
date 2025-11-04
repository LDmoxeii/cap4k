package com.only4.cap4k.ddd.application

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
 *
 * @author LD_moxeii
 * @date 2025/07/28
 */
open class JpaUnitOfWork(
    private val uowInterceptors: List<UnitOfWorkInterceptor>,
    private val persistListenerManager: PersistListenerManager,
    private val supportEntityInlinePersistListener: Boolean,
    private val supportValueObjectExistsCheckOnSave: Boolean
) : UnitOfWork {

    @PersistenceContext
    lateinit var entityManager: EntityManager

    companion object {
        private val log = LoggerFactory.getLogger(JpaUnitOfWork::class.java)

        lateinit var instance: JpaUnitOfWork

        fun fixAopWrapper(unitOfWork: JpaUnitOfWork) {
            instance = unitOfWork
        }

        private val persistEntitiesThreadLocal = ThreadLocal.withInitial { LinkedHashSet<Any>() }
        private val removeEntitiesThreadLocal = ThreadLocal.withInitial { LinkedHashSet<Any>() }
        private val processingEntitiesThreadLocal = ThreadLocal.withInitial { LinkedHashSet<Any>() }
        private val wrapperMapThreadLocal = ThreadLocal.withInitial { HashMap<Any, Aggregate<*>>() }

        private val entityInformationCache = ConcurrentHashMap<Class<*>, EntityInformation<*, *>>()

        @JvmStatic
        fun reset() {
            persistEntitiesThreadLocal.remove()
            removeEntitiesThreadLocal.remove()
            processingEntitiesThreadLocal.remove()
            wrapperMapThreadLocal.remove()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getEntityInformation(entityClass: Class<*>): EntityInformation<Any, Any> =
        entityInformationCache.computeIfAbsent(entityClass) {
            JpaEntityInformationSupport.getEntityInformation(it, entityManager)
        } as EntityInformation<Any, Any>

    private fun isValueObjectAndExists(entity: Any): Boolean {
        val valueObject = entity as? ValueObject<*> ?: return false
        val id = valueObject.hash()
        return entityManager.find(entity.javaClass, id) != null
    }

    private fun isExists(entity: Any): Boolean {
        val id = when (entity) {
            is ValueObject<*> -> entity.hash()
            else -> {
                val entityInformation = getEntityInformation(entity.javaClass)
                if (entityInformation.isNew(entity)) return false
                entityInformation.getId(entity)
            }
        }

        return entityManager.find(entity.javaClass, id) != null
    }

    protected open fun persistenceContextEntities(): List<Any> = try {
        val sessionImplementor = entityManager.unwrap(SessionImplementor::class.java)
        if (sessionImplementor.isOpen) {
            sessionImplementor.persistenceContext
                .reentrantSafeEntityEntries()
                .map { it.key }
        } else {
            emptyList()
        }
    } catch (ex: Exception) {
        log.debug("获取持久化上下文实体失败", ex)
        emptyList()
    }

    protected open fun onEntitiesFlushed(
        createdEntities: Set<Any>,
        updatedEntities: Set<Any>,
        deletedEntities: Set<Any>
    ) {
        if (!supportEntityInlinePersistListener) return

        createdEntities.forEach { persistListenerManager.onChange(it, PersistType.CREATE) }
        updatedEntities.forEach { persistListenerManager.onChange(it, PersistType.UPDATE) }
        deletedEntities.forEach { persistListenerManager.onChange(it, PersistType.DELETE) }
    }

    private fun unwrapEntity(entity: Any): Any {
        if (entity !is Aggregate<*>) return entity

        val unwrappedEntity = entity._unwrap()
        wrapperMapThreadLocal.get()[unwrappedEntity] = entity
        return unwrappedEntity
    }

    private fun updateWrappedEntity(entity: Any, updatedEntity: Any) {
        val wrapperMap = wrapperMapThreadLocal.get()
        val aggregate = wrapperMap.remove(entity) ?: return

        @Suppress("UNCHECKED_CAST")
        (aggregate as Aggregate<Any>)._wrap(updatedEntity)
        wrapperMap[updatedEntity] = aggregate
    }

    override fun persist(entity: Any) {
        val unwrappedEntity = unwrapEntity(entity)
        if (isValueObjectAndExists(unwrappedEntity)) return
        persistEntitiesThreadLocal.get().add(unwrappedEntity)
    }

    override fun persistIfNotExist(entity: Any): Boolean {
        val unwrappedEntity = unwrapEntity(entity)
        if (isExists(unwrappedEntity)) return false
        return persistEntitiesThreadLocal.get().add(unwrappedEntity)
    }

    override fun remove(entity: Any) {
        val unwrappedEntity = unwrapEntity(entity)
        removeEntitiesThreadLocal.get().add(unwrappedEntity)
    }

    private fun pushProcessingEntity(
        entity: Any,
        currentProcessedPersistenceContextEntities: MutableSet<Any>
    ): Boolean {
        val processingEntities = processingEntitiesThreadLocal.get()
        val added = processingEntities.add(entity)
        if (added) currentProcessedPersistenceContextEntities.add(entity)
        return added
    }

    private fun popProcessingEntities(currentProcessedPersistenceContextEntities: Set<Any>): Boolean =
        currentProcessedPersistenceContextEntities.isEmpty() ||
            processingEntitiesThreadLocal.get().removeAll(currentProcessedPersistenceContextEntities)

    override fun save(propagation: Propagation) {
        val currentProcessedEntitySet = LinkedHashSet<Any>()

        val persistEntitySet = persistEntitiesThreadLocal.get().takeIf { it.isNotEmpty() }?.also { entities ->
            persistEntitiesThreadLocal.remove()
            entities.forEach { pushProcessingEntity(it, currentProcessedEntitySet) }
        } ?: emptySet()

        val deleteEntitySet = removeEntitiesThreadLocal.get().takeIf { it.isNotEmpty() }?.also { entities ->
            removeEntitiesThreadLocal.remove()
            entities.forEach { pushProcessingEntity(it, currentProcessedEntitySet) }
        } ?: emptySet()

        uowInterceptors.forEach { it.beforeTransaction(persistEntitySet, deleteEntitySet) }

        try {
            save(arrayOf(persistEntitySet, deleteEntitySet, currentProcessedEntitySet), propagation) { input ->
                val (persistEntities, deleteEntities, processedEntities) = input

                val results = object {
                    val created = LinkedHashSet<Any>()
                    val updated = LinkedHashSet<Any>()
                    val deleted = LinkedHashSet<Any>()
                    var needsFlush = false
                    var refreshList: MutableList<Any>? = null
                }

                uowInterceptors.forEach { it.preInTransaction(persistEntities, deleteEntities) }

                // 处理持久化实体
                persistEntities.takeIf { it.isNotEmpty() }?.let { entities ->
                    results.needsFlush = true
                    entities.forEach { entity ->
                        when {
                            // ValueObject 存在性检查
                            supportValueObjectExistsCheckOnSave && entity is ValueObject<*> -> {
                                if (!isExists(entity)) {
                                    entityManager.persist(entity)
                                    results.created.add(entity)
                                }
                            }
                            // 新实体处理
                            getEntityInformation(entity.javaClass).isNew(entity) -> {
                                if (!entityManager.contains(entity)) {
                                    entityManager.persist(entity)
                                }
                                results.refreshList = (results.refreshList ?: mutableListOf()).apply { add(entity) }
                                results.created.add(entity)
                            }
                            // 现有实体处理
                            else -> {
                                if (!entityManager.contains(entity)) {
                                    entityManager.merge(entity).also { merged -> updateWrappedEntity(entity, merged) }
                                }
                                results.updated.add(entity)
                            }
                        }
                    }
                }

                // 处理删除实体
                deleteEntities.takeIf { it.isNotEmpty() }?.let { entities ->
                    results.needsFlush = true
                    entities.forEach { entity ->
                        when {
                            entityManager.contains(entity) -> entityManager.remove(entity)
                            else -> {
                                entityManager.merge(entity).also { merged ->
                                    updateWrappedEntity(entity, merged)
                                    entityManager.remove(merged)
                                }
                            }
                        }
                        results.deleted.add(entity)
                    }
                }

                // 刷新和回调处理
                if (results.needsFlush) {
                    entityManager.flush()
                    results.refreshList?.forEach { entityManager.refresh(it) }
                    onEntitiesFlushed(results.created, results.updated, results.deleted)
                }

                // 后处理
                buildSet {
                    addAll(persistEntities)
                    addAll(deleteEntities)
//                    persistenceContextEntities().forEach {
//                        pushProcessingEntity(it, processedEntities as MutableSet<Any>)
//                    }
                    addAll(processedEntities)
                }.let { allEntities ->
                    uowInterceptors.forEach { it.postEntitiesPersisted(allEntities) }
                    uowInterceptors.forEach { it.postInTransaction(persistEntities, deleteEntities) }
                }
            }

            uowInterceptors.forEach { it.afterTransaction(persistEntitySet, deleteEntitySet) }
        } finally {
            popProcessingEntities(currentProcessedEntitySet)
        }
    }

    fun interface TransactionHandler<I, O> {
        fun exec(input: I): O
    }

    fun <I, O> save(input: I, propagation: Propagation, transactionHandler: TransactionHandler<I, O>): O =
        when (propagation) {
            Propagation.SUPPORTS -> instance.supports(input, transactionHandler)
            Propagation.NOT_SUPPORTED -> instance.notSupported(input, transactionHandler)
            Propagation.REQUIRES_NEW -> instance.requiresNew(input, transactionHandler)
            Propagation.MANDATORY -> instance.mandatory(input, transactionHandler)
            Propagation.NEVER -> instance.never(input, transactionHandler)
            Propagation.NESTED -> instance.nested(input, transactionHandler)
            else -> instance.required(input, transactionHandler)
        }

    @Transactional(rollbackFor = [Exception::class], propagation = Propagation.REQUIRED)
    open fun <I, O> required(input: I, transactionHandler: TransactionHandler<I, O>): O =
        transactionWrapper(input, transactionHandler)

    @Transactional(rollbackFor = [Exception::class], propagation = Propagation.REQUIRES_NEW)
    open fun <I, O> requiresNew(input: I, transactionHandler: TransactionHandler<I, O>): O =
        transactionWrapper(input, transactionHandler)

    @Transactional(rollbackFor = [Exception::class], propagation = Propagation.SUPPORTS)
    open fun <I, O> supports(input: I, transactionHandler: TransactionHandler<I, O>): O =
        transactionWrapper(input, transactionHandler)

    @Transactional(rollbackFor = [Exception::class], propagation = Propagation.NOT_SUPPORTED)
    open fun <I, O> notSupported(input: I, transactionHandler: TransactionHandler<I, O>): O =
        transactionWrapper(input, transactionHandler)

    @Transactional(rollbackFor = [Exception::class], propagation = Propagation.MANDATORY)
    open fun <I, O> mandatory(input: I, transactionHandler: TransactionHandler<I, O>): O =
        transactionWrapper(input, transactionHandler)

    @Transactional(rollbackFor = [Exception::class], propagation = Propagation.NEVER)
    open fun <I, O> never(input: I, transactionHandler: TransactionHandler<I, O>): O =
        transactionWrapper(input, transactionHandler)

    @Transactional(rollbackFor = [Exception::class], propagation = Propagation.NESTED)
    open fun <I, O> nested(input: I, transactionHandler: TransactionHandler<I, O>): O =
        transactionWrapper(input, transactionHandler)

    protected open fun <I, O> transactionWrapper(input: I, transactionHandler: TransactionHandler<I, O>): O =
        transactionHandler.exec(input)
}
