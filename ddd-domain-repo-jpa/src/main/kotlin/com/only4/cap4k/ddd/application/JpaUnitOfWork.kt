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
            private set

        fun fixAopWrapper(unitOfWork: JpaUnitOfWork) {
            instance = unitOfWork
        }

        private val persistEntitiesThreadLocal = ThreadLocal.withInitial { LinkedHashSet<Any>() }
        private val removeEntitiesThreadLocal = ThreadLocal.withInitial { LinkedHashSet<Any>() }
        private val processingEntitiesThreadLocal = ThreadLocal.withInitial { LinkedHashSet<Any>() }
        private val wrapperMapThreadLocal = ThreadLocal.withInitial { HashMap<Any, Aggregate<*>>() }

        private val entityInformationCache = ConcurrentHashMap<Class<*>, EntityInformation<*, *>>()

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
        val entityInformation = getEntityInformation(entity.javaClass)
        val isValueObject = entity is ValueObject<*>

        if (!isValueObject && entityInformation.isNew(entity)) {
            return false
        }

        val id = if (isValueObject) {
            (entity as ValueObject<*>).hash()
        } else {
            entityInformation.getId(entity)
        }

        return id != null && entityManager.find(entity.javaClass, id) != null
    }

    protected fun persistenceContextEntities(): List<Any> = try {
        val sessionImplementor = entityManager.delegate as SessionImplementor
        if (!sessionImplementor.isClosed) {
            sessionImplementor.persistenceContext
                .reentrantSafeEntityEntries()
                .map { it.key }
        } else {
            emptyList()
        }
    } catch (ex: Exception) {
        log.debug("跟踪实体获取失败", ex)
        emptyList()
    }

    protected fun onEntitiesFlushed(
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

    override fun save() {
        save(Propagation.REQUIRED)
    }

    private fun pushProcessingEntities(
        entity: Any,
        currentProcessedPersistenceContextEntities: MutableSet<Any>
    ): Boolean {
        return if (entity !in processingEntitiesThreadLocal.get()) {
            processingEntitiesThreadLocal.get().add(entity)
            currentProcessedPersistenceContextEntities.add(entity)
            true
        } else {
            false
        }
    }

    private fun popProcessingEntities(currentProcessedPersistenceContextEntities: Set<Any>): Boolean =
        if (currentProcessedPersistenceContextEntities.isNotEmpty()) {
            processingEntitiesThreadLocal.get().removeAll(currentProcessedPersistenceContextEntities)
        } else {
            true
        }

    override fun save(propagation: Propagation) {
        val currentProcessedEntitySet = LinkedHashSet<Any>()
        val persistEntitySet = persistEntitiesThreadLocal.get()

        if (persistEntitySet.isNotEmpty()) {
            persistEntitiesThreadLocal.remove()
            persistEntitySet.forEach { pushProcessingEntities(it, currentProcessedEntitySet) }
        }

        val deleteEntitySet = removeEntitiesThreadLocal.get()
        if (deleteEntitySet.isNotEmpty()) {
            removeEntitiesThreadLocal.remove()
            deleteEntitySet.forEach { pushProcessingEntities(it, currentProcessedEntitySet) }
        }

        uowInterceptors.forEach { it.beforeTransaction(persistEntitySet, deleteEntitySet) }

        val saveAndDeleteEntities = arrayOf(persistEntitySet, deleteEntitySet, currentProcessedEntitySet)

        save(saveAndDeleteEntities, propagation) { input ->
            val (persistEntities, deleteEntities, processedEntities) = input
            val createdEntities = LinkedHashSet<Any>()
            val updatedEntities = LinkedHashSet<Any>()
            val deletedEntities = LinkedHashSet<Any>()

            uowInterceptors.forEach { it.preInTransaction(persistEntities, deleteEntities) }

            var flush = false
            var refreshEntityList: MutableList<Any>? = null

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

                    val entityInformation = getEntityInformation(entity.javaClass)
                    if (entityInformation.isNew(entity)) {
                        if (!entityManager.contains(entity)) {
                            entityManager.persist(entity)
                        }
                        if (refreshEntityList == null) {
                            refreshEntityList = mutableListOf()
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

            if (flush) {
                entityManager.flush()
                refreshEntityList?.forEach { entityManager.refresh(it) }
                onEntitiesFlushed(createdEntities, updatedEntities, deletedEntities)
            }

            val entities = LinkedHashSet<Any>().apply {
                addAll(persistEntities)
                addAll(deleteEntities)
            }

            persistenceContextEntities().forEach {
                pushProcessingEntities(it, processedEntities as MutableSet<Any>)
            }
            entities.addAll(processedEntities)

            uowInterceptors.forEach { it.postEntitiesPersisted(entities) }
            uowInterceptors.forEach { it.postInTransaction(persistEntities, deleteEntities) }
        }

        uowInterceptors.forEach { it.afterTransaction(persistEntitySet, deleteEntitySet) }
        popProcessingEntities(currentProcessedEntitySet)
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

    protected fun <I, O> transactionWrapper(input: I, transactionHandler: TransactionHandler<I, O>): O =
        transactionHandler.exec(input)
}
