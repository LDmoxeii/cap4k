package com.only4.cap4k.ddd.application

import com.only4.cap4k.ddd.core.application.PersistIntent
import com.only4.cap4k.ddd.core.application.UnitOfWorkInterceptor
import com.only4.cap4k.ddd.core.domain.id.ApplicationSideId
import com.only4.cap4k.ddd.core.domain.id.BuiltInIdentifierStrategies
import com.only4.cap4k.ddd.core.domain.id.IdentifierCapability
import com.only4.cap4k.ddd.core.domain.id.IdentifierStrategy
import com.only4.cap4k.ddd.core.domain.id.IdentifierStrategyRegistry
import com.only4.cap4k.ddd.core.domain.id.MapBackedIdentifierStrategyRegistry
import com.only4.cap4k.ddd.core.domain.id.StrongId
import com.only4.cap4k.ddd.core.domain.repo.AggregateLoadPlan
import com.only4.cap4k.ddd.core.domain.repo.PersistListenerManager
import com.only4.cap4k.ddd.core.domain.repo.PersistType
import io.mockk.*
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.EntityManager
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.transaction.annotation.Propagation
import java.io.Serializable
import kotlin.reflect.KClass

@DisplayName("JpaUnitOfWork 测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class JpaUnitOfWorkTest {

    private lateinit var entityManager: EntityManager
    private lateinit var persistListenerManager: PersistListenerManager
    private lateinit var uowInterceptors: List<UnitOfWorkInterceptor>
    private lateinit var interceptor1: UnitOfWorkInterceptor
    private lateinit var interceptor2: UnitOfWorkInterceptor
    private lateinit var jpaUnitOfWork: TestableJpaUnitOfWork
    private lateinit var mockEntityInfo: org.springframework.data.jpa.repository.support.JpaEntityInformation<Any, Any>

    // Testable subclass to access protected members
    class TestableJpaUnitOfWork(
        uowInterceptors: List<UnitOfWorkInterceptor>,
        persistListenerManager: PersistListenerManager,
        supportEntityInlinePersistListener: Boolean,
        idStrategyRegistry: IdentifierStrategyRegistry = MapBackedIdentifierStrategyRegistry(emptyList()),
        private val dirtyExistingEntities: Set<Any> = emptySet(),
    ) : JpaUnitOfWork(
        uowInterceptors,
        persistListenerManager,
        supportEntityInlinePersistListener,
        idStrategyRegistry
    ) {

        fun setTestEntityManager(em: EntityManager) {
            this.entityManager = em
        }

        override fun dirtyExistingEntities(existingEntities: Set<Any>): Set<Any> =
            existingEntities.filterTo(LinkedHashSet()) { candidate ->
                dirtyExistingEntities.any { configured -> configured === candidate }
            }
    }

    @BeforeEach
    fun setUp() {
        // Create fresh mocks for each test
        entityManager = mockk()
        persistListenerManager = mockk(relaxed = true)
        interceptor1 = mockk(relaxed = true)
        interceptor2 = mockk(relaxed = true)
        uowInterceptors = listOf(interceptor1, interceptor2)

        jpaUnitOfWork = TestableJpaUnitOfWork(
            uowInterceptors = uowInterceptors,
            persistListenerManager = persistListenerManager,
            supportEntityInlinePersistListener = true,
            idStrategyRegistry = MapBackedIdentifierStrategyRegistry(listOf(FixedLongStrategy())),
        )

        // Set up entity manager
        jpaUnitOfWork.setTestEntityManager(entityManager)
        JpaUnitOfWork.fixAopWrapper(jpaUnitOfWork)

        // Reset ThreadLocal state
        JpaUnitOfWork.reset()
        clearEntityInformationCache()

        // Set up static mock and create fresh entity info mock each time
        mockkStatic("org.springframework.data.jpa.repository.support.JpaEntityInformationSupport")
        mockEntityInfo =
            mockk<org.springframework.data.jpa.repository.support.JpaEntityInformation<Any, Any>>(relaxed = true)
        every {
            org.springframework.data.jpa.repository.support.JpaEntityInformationSupport.getEntityInformation(
                any<Class<*>>(),
                any()
            )
        } returns mockEntityInfo
        every { mockEntityInfo.isNew(any()) } returns true
        every { mockEntityInfo.getId(any()) } returns null

        // Mock entityManager methods explicitly
        every { entityManager.persist(any()) } just Runs
        every { entityManager.merge<Any>(any()) } answers { firstArg<Any>() }
        every { entityManager.contains(any()) } returns false
        every { entityManager.remove(any()) } just Runs
        every { entityManager.flush() } just Runs
        every { entityManager.refresh(any()) } just Runs
    }

    @AfterEach
    fun tearDown() {
        JpaUnitOfWork.reset()
        // Clear recorded calls and verification marks, but keep answers
        clearMocks(
            entityManager,
            persistListenerManager,
            interceptor1,
            interceptor2,
            mockEntityInfo,
            answers = false,
            recordedCalls = true
        )
    }

    private fun clearEntityInformationCache() {
        val field = JpaUnitOfWork::class.java.getDeclaredField("entityInformationCache")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(null) as MutableMap<Class<*>, *>).clear()
    }

    private fun processingEntityCount(): Int {
        val field = JpaUnitOfWork::class.java.getDeclaredField("processingEntitiesThreadLocal")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val threadLocal = field.get(null) as ThreadLocal<Set<Any>>
        return threadLocal.get().size
    }

    @Test
    @DisplayName("repository observation records root and generated owned children")
    fun repositoryObservationRecordsRootAndGeneratedOwnedChildren() {
        val child = ObservedChild(20L)
        val root = ObservedRoot(10L, mutableListOf(child))
        every { mockEntityInfo.isNew(root) } returns false
        every { mockEntityInfo.getId(root) } returns 10L
        every { mockEntityInfo.isNew(child) } returns false
        every { mockEntityInfo.getId(child) } returns 20L

        jpaUnitOfWork.observeRepositoryLoad(root, AggregateLoadPlan.WHOLE_AGGREGATE)

        val baseline = jpaUnitOfWork.observedRepositoryBaseline()
        val entries = baseline.entriesFor(root)
        assertEquals(listOf(root, child), entries.map { it.entity })
        assertTrue(baseline.isObservedObject(root))
        assertTrue(baseline.isObservedObject(child))
        assertTrue(baseline.containsIdentity(JpaObservedIdentity(ObservedRoot::class.java, 10L)))
        assertTrue(baseline.containsIdentity(JpaObservedIdentity(ObservedChild::class.java, 20L)))
    }

    @Test
    @DisplayName("default persist enrolls an observed detached entity without reporting update")
    fun defaultPersistShouldEnrollObservedDetachedExistingEntity() {
        val entity = TestEntity(1L, "existing")
        every { mockEntityInfo.isNew(entity) } returns false
        every { mockEntityInfo.getId(entity) } returns 1L
        every { entityManager.contains(entity) } returns false
        jpaUnitOfWork.observeRepositoryLoad(entity, AggregateLoadPlan.WHOLE_AGGREGATE)

        jpaUnitOfWork.persist(entity)
        jpaUnitOfWork.save()

        verify { entityManager.merge(entity) }
        verify(exactly = 0) { entityManager.persist(entity) }
        verify(exactly = 0) { persistListenerManager.onChange(entity, PersistType.UPDATE) }
    }

    @Test
    @DisplayName("default EXISTING persist rejects an assigned detached entity without trustworthy evidence")
    fun defaultPersistShouldRejectAssignedDetachedEntityWithoutBaseline() {
        val entity = ApplicationSideLongEntity(id = 100L, name = "unobserved")
        every { mockEntityInfo.isNew(entity) } returns false
        every { mockEntityInfo.getId(entity) } returns 100L
        every { entityManager.contains(entity) } returns false

        val error = assertThrows(IllegalStateException::class.java) {
            jpaUnitOfWork.persist(entity)
        }

        assertTrue(error.message!!.contains("repository observation baseline or provider-managed existing state"))
        verify(exactly = 0) { entityManager.merge(entity) }
        verify(exactly = 0) { entityManager.persist(entity) }
    }

    @Test
    @DisplayName("CREATE intent should persist a new entity and report CREATE")
    fun createIntentShouldPersistAndReportCreate() {
        val entity = TestEntity(null, "new")
        every { mockEntityInfo.isNew(entity) } returns true

        jpaUnitOfWork.persist(entity, PersistIntent.CREATE)
        jpaUnitOfWork.save()

        verify { entityManager.persist(entity) }
        verify { entityManager.flush() }
        verify { entityManager.refresh(entity) }
        verify { persistListenerManager.onChange(entity, PersistType.CREATE) }
        verify(exactly = 0) { entityManager.merge(entity) }
    }

    @Test
    @DisplayName("CREATE persist assigns generated strong root id before save")
    fun createPersistShouldAssignGeneratedStrongRootIdBeforeSave() {
        val entity = StrongRootEntity()

        jpaUnitOfWork.persist(entity, PersistIntent.CREATE)

        assertEquals("018f0000-0000-7000-8000-000000000001", entity.id.value)
    }

    @Test
    @DisplayName("EXISTING persist fills new owned child strong id without replacing root id")
    fun existingPersistShouldFillNewOwnedChildStrongIdWithoutReplacingRootId() {
        val root = StrongRootEntity()
        root.id = TestStrongEntityId("018f0000-0000-7000-8000-000000000099")
        val observedChild = StrongChildEntity().also {
            it.id = TestStrongEntityId("018f0000-0000-7000-8000-000000000098")
        }
        root.children += observedChild
        every { mockEntityInfo.isNew(root) } returns false
        every { mockEntityInfo.getId(root) } returns root.id
        every { mockEntityInfo.isNew(observedChild) } returns false
        every { mockEntityInfo.getId(observedChild) } returns observedChild.id

        jpaUnitOfWork.observeRepositoryLoad(root, AggregateLoadPlan.WHOLE_AGGREGATE)
        val child = StrongChildEntity()
        root.children += child
        jpaUnitOfWork.persist(root)

        assertEquals("018f0000-0000-7000-8000-000000000099", root.id.value)
        assertEquals("018f0000-0000-7000-8000-000000000098", observedChild.id.value)
        assertEquals("018f0000-0000-7000-8000-000000000001", child.id.value)
    }

    @Test
    @DisplayName("repeated repository observation does not absorb a child added after the original baseline")
    fun repeatedObservationShouldPreserveOriginalBaseline() {
        val root = StrongRootEntity().also {
            it.id = TestStrongEntityId("018f0000-0000-7000-8000-000000000091")
        }
        val observedChild = StrongChildEntity().also {
            it.id = TestStrongEntityId("018f0000-0000-7000-8000-000000000092")
        }
        root.children += observedChild
        every { mockEntityInfo.isNew(root) } returns false
        every { mockEntityInfo.getId(root) } returns root.id
        every { mockEntityInfo.isNew(observedChild) } returns false
        every { mockEntityInfo.getId(observedChild) } returns observedChild.id
        jpaUnitOfWork.observeRepositoryLoad(root, AggregateLoadPlan.WHOLE_AGGREGATE)

        val newChild = StrongChildEntity()
        root.children += newChild
        jpaUnitOfWork.observeRepositoryLoad(root, AggregateLoadPlan.WHOLE_AGGREGATE)
        jpaUnitOfWork.persist(root)

        assertEquals("018f0000-0000-7000-8000-000000000001", newChild.id.value)
    }

    @Test
    @DisplayName("EXISTING persist validates a removed baseline child identity before flush")
    fun existingPersistShouldRejectMissingIdentityOnRemovedBaselineChild() {
        val child = ObservedChild(20L)
        val root = ObservedRoot(10L, mutableListOf(child))
        every { mockEntityInfo.isNew(root) } returns false
        every { mockEntityInfo.getId(root) } returns root.id
        every { mockEntityInfo.isNew(child) } answers { child.id == null }
        every { mockEntityInfo.getId(child) } answers { child.id }
        jpaUnitOfWork.observeRepositoryLoad(root, AggregateLoadPlan.WHOLE_AGGREGATE)

        root.children.remove(child)
        child.id = null

        val error = assertThrows(IllegalStateException::class.java) {
            jpaUnitOfWork.persist(root)
        }

        assertTrue(error.message!!.contains("changed identity"))
        verify(exactly = 0) { entityManager.merge(root) }
        verify(exactly = 0) { entityManager.flush() }
    }

    @Test
    @DisplayName("EXISTING persist keeps legitimate orphan removal when baseline identity is unchanged")
    fun existingPersistShouldAllowRemovedBaselineChildWithUnchangedIdentity() {
        val child = ObservedChild(20L)
        val root = ObservedRoot(10L, mutableListOf(child))
        every { mockEntityInfo.isNew(root) } returns false
        every { mockEntityInfo.getId(root) } returns root.id
        every { mockEntityInfo.isNew(child) } returns false
        every { mockEntityInfo.getId(child) } returns child.id
        jpaUnitOfWork.observeRepositoryLoad(root, AggregateLoadPlan.WHOLE_AGGREGATE)

        root.children.remove(child)
        jpaUnitOfWork.persist(root)
        jpaUnitOfWork.save()

        verify { entityManager.merge(root) }
        verify { entityManager.flush() }
    }

    @Test
    @DisplayName("CREATE completes a generated owned child added by preInTransaction before persistence")
    fun createShouldCompleteChildAddedByPreInTransaction() {
        val root = StrongRootEntity()
        val child = StrongChildEntity()
        every { interceptor1.preInTransaction(any(), any()) } answers {
            root.children += child
        }

        jpaUnitOfWork.persist(root, PersistIntent.CREATE)
        jpaUnitOfWork.save()

        assertEquals("018f0000-0000-7000-8000-000000000001", child.id.value)
        verify { entityManager.persist(root) }
    }

    @Test
    @DisplayName("EXISTING completes a generated owned child added by preInTransaction before persistence")
    fun existingShouldCompleteChildAddedByPreInTransaction() {
        val root = StrongRootEntity().also {
            it.id = TestStrongEntityId("018f0000-0000-7000-8000-000000000093")
        }
        val child = StrongChildEntity()
        every { mockEntityInfo.isNew(root) } returns false
        every { mockEntityInfo.getId(root) } returns root.id
        jpaUnitOfWork.observeRepositoryLoad(root, AggregateLoadPlan.WHOLE_AGGREGATE)
        every { interceptor1.preInTransaction(any(), any()) } answers {
            root.children += child
        }

        jpaUnitOfWork.persist(root)
        jpaUnitOfWork.save()

        assertEquals("018f0000-0000-7000-8000-000000000001", child.id.value)
        verify { entityManager.merge(root) }
    }

    @Test
    @DisplayName("EXISTING persist rejects a missing root strong id before completing owned children")
    fun existingPersistShouldRejectMissingRootStrongIdBeforeCompletingOwnedChildren() {
        val root = StrongRootEntity()
        val child = StrongChildEntity()
        root.children += child
        every { entityManager.contains(root) } returns true

        val error = assertThrows(IllegalStateException::class.java) {
            jpaUnitOfWork.persist(root)
        }

        assertTrue(error.message!!.contains("missing Strong ID"))
        assertThrows(UninitializedPropertyAccessException::class.java) { root.id }
        assertThrows(UninitializedPropertyAccessException::class.java) { child.id }
    }

    @Test
    @DisplayName("EXISTING persist rejects an observed entity whose strong id changed")
    fun existingPersistShouldRejectChangedObservedStrongId() {
        val root = StrongRootEntity()
        val observedId = TestStrongEntityId("018f0000-0000-7000-8000-000000000097")
        root.id = observedId
        every { mockEntityInfo.isNew(root) } returns false
        every { mockEntityInfo.getId(root) } returns observedId
        jpaUnitOfWork.observeRepositoryLoad(root, AggregateLoadPlan.WHOLE_AGGREGATE)

        root.id = TestStrongEntityId("018f0000-0000-7000-8000-000000000096")

        val error = assertThrows(IllegalStateException::class.java) {
            jpaUnitOfWork.persist(root)
        }

        assertTrue(error.message!!.contains("changed identity"))
    }

    @Test
    @DisplayName("EXISTING revalidates observed identity after beforeTransaction interceptors")
    fun existingShouldRejectObservedIdentityChangedByBeforeTransactionInterceptor() {
        val root = StrongRootEntity()
        root.id = TestStrongEntityId("018f0000-0000-7000-8000-000000000095")
        every { mockEntityInfo.isNew(root) } returns false
        every { mockEntityInfo.getId(root) } answers { root.id }
        jpaUnitOfWork.observeRepositoryLoad(root, AggregateLoadPlan.WHOLE_AGGREGATE)
        jpaUnitOfWork.persist(root)
        every { interceptor1.beforeTransaction(any(), any()) } answers {
            root.id = TestStrongEntityId("018f0000-0000-7000-8000-000000000094")
        }

        val error = assertThrows(IllegalStateException::class.java) {
            jpaUnitOfWork.save()
        }

        assertTrue(error.message!!.contains("changed identity"))
        verify(exactly = 0) { entityManager.merge(root) }
        verify(exactly = 0) { entityManager.flush() }
    }

    @Test
    @DisplayName("generated-id CREATE should decide refresh before persist changes isNew")
    fun generatedIdCreateShouldDecideRefreshBeforePersistChangesIsNew() {
        val entity = TestEntity(null, "generated")
        var persisted = false
        every { mockEntityInfo.isNew(entity) } answers { !persisted }
        every { entityManager.persist(entity) } answers { persisted = true }

        jpaUnitOfWork.persist(entity, PersistIntent.CREATE)
        jpaUnitOfWork.save()

        verify { entityManager.persist(entity) }
        verify { entityManager.refresh(entity) }
        verify { persistListenerManager.onChange(entity, PersistType.CREATE) }
    }

    @Test
    @DisplayName("equal CREATE instances should remain distinct through persistence notifications and interceptors")
    fun equalCreateInstancesShouldRemainDistinctThroughPersistenceNotificationsAndInterceptors() {
        val first = TestEntity(null, "same")
        val second = TestEntity(null, "same")
        val persisted = mutableListOf<Any>()
        val notified = mutableListOf<Any>()
        val intercepted = mutableListOf<Set<Any>>()
        every { entityManager.persist(capture(persisted)) } just Runs
        every { persistListenerManager.onChange(capture(notified), PersistType.CREATE) } just Runs
        every { interceptor1.beforeTransaction(capture(intercepted), any()) } just Runs

        jpaUnitOfWork.persist(first, PersistIntent.CREATE)
        jpaUnitOfWork.persist(second, PersistIntent.CREATE)
        jpaUnitOfWork.save()

        assertEquals(2, persisted.size)
        assertSame(first, persisted[0])
        assertSame(second, persisted[1])
        assertEquals(2, notified.size)
        assertSame(first, notified[0])
        assertSame(second, notified[1])
        assertEquals(1, intercepted.size)
        assertEquals(2, intercepted.single().size)
        assertSame(first, intercepted.single().elementAt(0))
        assertSame(second, intercepted.single().elementAt(1))
    }

    @Test
    @DisplayName("managed existing intent should not report update without dirty classification")
    fun managedExistingIntentShouldNotReportUpdateWithoutDirtyClassification() {
        val entity = TestEntity(1L, "managed")
        every { mockEntityInfo.isNew(entity) } returns false
        every { mockEntityInfo.getId(entity) } returns 1L
        every { entityManager.contains(entity) } returns true

        jpaUnitOfWork.persist(entity)
        jpaUnitOfWork.save()

        verify(exactly = 0) { entityManager.merge(entity) }
        verify(exactly = 0) { entityManager.persist(entity) }
        verify(exactly = 0) { persistListenerManager.onChange(entity, PersistType.UPDATE) }
    }

    @Test
    @DisplayName("clean existing entity does not emit update listener")
    fun cleanExistingEntityShouldNotEmitUpdateListener() {
        val entity = TestEntity(1L, "clean")
        every { mockEntityInfo.isNew(entity) } returns false
        every { mockEntityInfo.getId(entity) } returns 1L
        every { entityManager.contains(entity) } returns true

        jpaUnitOfWork.persist(entity)
        jpaUnitOfWork.save()

        verify(exactly = 0) { persistListenerManager.onChange(entity, PersistType.UPDATE) }
    }

    @Test
    @DisplayName("clean detached existing entity is inspected through its managed merge result")
    fun cleanDetachedExistingEntityShouldNotEmitUpdateListener() {
        val detached = TestEntity(1L, "clean")
        val managed = TestEntity(1L, "clean")
        jpaUnitOfWork = TestableJpaUnitOfWork(
            uowInterceptors = uowInterceptors,
            persistListenerManager = persistListenerManager,
            supportEntityInlinePersistListener = true,
            idStrategyRegistry = MapBackedIdentifierStrategyRegistry(listOf(FixedLongStrategy())),
            dirtyExistingEntities = setOf(detached),
        )
        jpaUnitOfWork.setTestEntityManager(entityManager)
        JpaUnitOfWork.fixAopWrapper(jpaUnitOfWork)
        every { mockEntityInfo.isNew(detached) } returns false
        every { mockEntityInfo.getId(detached) } returns 1L
        every { entityManager.contains(detached) } returns false
        every { entityManager.merge(detached) } returns managed
        jpaUnitOfWork.observeRepositoryLoad(detached, AggregateLoadPlan.WHOLE_AGGREGATE)

        jpaUnitOfWork.persist(detached)
        jpaUnitOfWork.save()

        verify { entityManager.merge(detached) }
        verify(exactly = 0) { persistListenerManager.onChange(any(), PersistType.UPDATE) }
    }

    @Test
    @DisplayName("dirty existing entity emits update listener")
    fun dirtyExistingEntityShouldEmitUpdateListener() {
        val entity = TestEntity(1L, "dirty")
        jpaUnitOfWork = TestableJpaUnitOfWork(
            uowInterceptors = uowInterceptors,
            persistListenerManager = persistListenerManager,
            supportEntityInlinePersistListener = true,
            idStrategyRegistry = MapBackedIdentifierStrategyRegistry(listOf(FixedLongStrategy())),
            dirtyExistingEntities = setOf(entity),
        )
        jpaUnitOfWork.setTestEntityManager(entityManager)
        JpaUnitOfWork.fixAopWrapper(jpaUnitOfWork)
        every { mockEntityInfo.isNew(entity) } returns false
        every { mockEntityInfo.getId(entity) } returns 1L
        every { entityManager.contains(entity) } returns true

        jpaUnitOfWork.persist(entity)
        jpaUnitOfWork.save()

        verify { persistListenerManager.onChange(entity, PersistType.UPDATE) }
    }

    @Test
    @DisplayName("same instance CREATE then default persist remains CREATE")
    fun sameInstanceCreateThenDefaultPersistRemainsCreate() {
        val entity = TestEntity(null, "created-then-mutated")
        every { mockEntityInfo.isNew(entity) } returns true

        jpaUnitOfWork.persist(entity, PersistIntent.CREATE)
        jpaUnitOfWork.persist(entity)
        jpaUnitOfWork.save()

        verify { entityManager.persist(entity) }
        verify { persistListenerManager.onChange(entity, PersistType.CREATE) }
        verify(exactly = 0) { entityManager.merge(entity) }
    }

    @Test
    @DisplayName("same instance CREATE then remove should cancel pending entry")
    fun sameInstanceCreateThenRemoveShouldCancelPendingEntry() {
        val entity = TestEntity(null, "cancelled")

        jpaUnitOfWork.persist(entity, PersistIntent.CREATE)
        jpaUnitOfWork.remove(entity)
        jpaUnitOfWork.save()

        verify(exactly = 0) { entityManager.persist(any()) }
        verify(exactly = 0) { entityManager.merge(any()) }
        verify(exactly = 0) { entityManager.remove(any()) }
        verify(exactly = 0) { entityManager.flush() }
        verify(exactly = 0) { persistListenerManager.onChange(any(), any()) }
    }

    @Test
    @DisplayName("same instance EXISTING then CREATE should fail fast")
    fun sameInstanceExistingThenCreateShouldFailFast() {
        val entity = TestEntity(1L, "existing")
        every { entityManager.contains(entity) } returns true

        jpaUnitOfWork.persist(entity)

        val error = assertThrows(IllegalStateException::class.java) {
            jpaUnitOfWork.persist(entity, PersistIntent.CREATE)
        }

        assertEquals("UoW intent conflict: EXISTING cannot become CREATE for the same instance", error.message)
    }

    @Test
    @DisplayName("same instance REMOVE then EXISTING should fail fast")
    fun sameInstanceRemoveThenExistingShouldFailFast() {
        val entity = TestEntity(1L, "removed")

        jpaUnitOfWork.remove(entity)

        val error = assertThrows(IllegalStateException::class.java) {
            jpaUnitOfWork.persist(entity)
        }

        assertTrue(error.message!!.contains("REMOVE cannot become EXISTING"))
    }

    @Test
    @DisplayName("应将实体添加到删除集合")
    fun testRemoveEntity() {
        // Given
        val entity = TestEntity(1L, "test")
        // Override default mock - for removal we need the entity to be in the context
        every { entityManager.contains(entity) } returns true

        // When
        jpaUnitOfWork.remove(entity)

        // Then
        jpaUnitOfWork.save()
        verify { entityManager.remove(entity) }
    }

    @Test
    @DisplayName("如果实体不在持久化上下文中应先合并再删除")
    fun testRemoveEntityNotInContext() {
        // Given
        val entity = TestEntity(1L, "test")
        val mergedEntity = TestEntity(1L, "test-merged")
        every { entityManager.contains(entity) } returns false
        every { entityManager.merge(entity) } returns mergedEntity

        // When
        jpaUnitOfWork.remove(entity)

        // Then
        jpaUnitOfWork.save()
        verify { entityManager.merge(entity) }
        verify { entityManager.remove(mergedEntity) }
    }

    @Test
    @DisplayName("默认应使用REQUIRED传播级别调用保存")
    fun testSaveWithDefaultPropagation() {
        // Given
        val entity = TestEntity(1L, "test")
        jpaUnitOfWork.persist(entity, PersistIntent.CREATE)

        // When
        jpaUnitOfWork.save()

        // Then
        verify { entityManager.persist(entity) }
        verify { entityManager.flush() }
    }

    @Test
    @DisplayName("保存时应按正确顺序调用拦截器")
    fun testSaveInterceptorOrder() {
        // Given
        val entity = TestEntity(1L, "test")
        jpaUnitOfWork.persist(entity, PersistIntent.CREATE)

        // When
        jpaUnitOfWork.save()

        // Then
        verifyOrder {
            interceptor1.beforeTransaction(any(), any())
            interceptor2.beforeTransaction(any(), any())
            interceptor1.preInTransaction(any(), any())
            interceptor2.preInTransaction(any(), any())
            interceptor1.postEntitiesPersisted(any())
            interceptor2.postEntitiesPersisted(any())
            interceptor1.postInTransaction(any(), any())
            interceptor2.postInTransaction(any(), any())
            interceptor1.afterTransaction(any(), any())
            interceptor2.afterTransaction(any(), any())
        }
    }

    @Test
    @DisplayName("没有实体需要处理时应跳过刷新")
    fun testSaveNoEntities() {
        // When
        jpaUnitOfWork.save()

        // Then
        verify(exactly = 0) { entityManager.flush() }
    }

    @Test
    @DisplayName("应处理REQUIRED事务传播")
    fun testTransactionPropagationRequired() {
        // Given
        val handler = mockk<JpaUnitOfWork.TransactionHandler<String, String>>()
        every { handler.exec("input") } returns "output"

        // When
        val result = jpaUnitOfWork.save("input", Propagation.REQUIRED, handler)

        // Then
        assertEquals("output", result)
        verify { handler.exec("input") }
    }

    @Test
    @DisplayName("应处理REQUIRES_NEW事务传播")
    fun testTransactionPropagationRequiresNew() {
        // Given
        val handler = mockk<JpaUnitOfWork.TransactionHandler<String, String>>()
        every { handler.exec("input") } returns "output"

        // When
        val result = jpaUnitOfWork.save("input", Propagation.REQUIRES_NEW, handler)

        // Then
        assertEquals("output", result)
        verify { handler.exec("input") }
    }

    @Test
    @DisplayName("应处理SUPPORTS事务传播")
    fun testTransactionPropagationSupports() {
        // Given
        val handler = mockk<JpaUnitOfWork.TransactionHandler<String, String>>()
        every { handler.exec("input") } returns "output"

        // When
        val result = jpaUnitOfWork.save("input", Propagation.SUPPORTS, handler)

        // Then
        assertEquals("output", result)
        verify { handler.exec("input") }
    }

    @Test
    @DisplayName("应处理NOT_SUPPORTED事务传播")
    fun testTransactionPropagationNotSupported() {
        // Given
        val handler = mockk<JpaUnitOfWork.TransactionHandler<String, String>>()
        every { handler.exec("input") } returns "output"

        // When
        val result = jpaUnitOfWork.save("input", Propagation.NOT_SUPPORTED, handler)

        // Then
        assertEquals("output", result)
        verify { handler.exec("input") }
    }

    @Test
    @DisplayName("应处理MANDATORY事务传播")
    fun testTransactionPropagationMandatory() {
        // Given
        val handler = mockk<JpaUnitOfWork.TransactionHandler<String, String>>()
        every { handler.exec("input") } returns "output"

        // When
        val result = jpaUnitOfWork.save("input", Propagation.MANDATORY, handler)

        // Then
        assertEquals("output", result)
        verify { handler.exec("input") }
    }

    @Test
    @DisplayName("应处理NEVER事务传播")
    fun testTransactionPropagationNever() {
        // Given
        val handler = mockk<JpaUnitOfWork.TransactionHandler<String, String>>()
        every { handler.exec("input") } returns "output"

        // When
        val result = jpaUnitOfWork.save("input", Propagation.NEVER, handler)

        // Then
        assertEquals("output", result)
        verify { handler.exec("input") }
    }

    @Test
    @DisplayName("应处理NESTED事务传播")
    fun testTransactionPropagationNested() {
        // Given
        val handler = mockk<JpaUnitOfWork.TransactionHandler<String, String>>()
        every { handler.exec("input") } returns "output"

        // When
        val result = jpaUnitOfWork.save("input", Propagation.NESTED, handler)

        // Then
        assertEquals("output", result)
        verify { handler.exec("input") }
    }

    @Test
    @DisplayName("应正确重置ThreadLocal变量")
    fun testReset() {
        // Given
        val entity = TestEntity(1L, "test")
        every { entityManager.contains(entity) } returns true
        jpaUnitOfWork.persist(entity)
        jpaUnitOfWork.remove(entity)

        // When
        JpaUnitOfWork.reset()

        // Then
        // After reset, save should not process any entities
        jpaUnitOfWork.save()
        verify(exactly = 0) { entityManager.persist(any()) }
        verify(exactly = 0) { entityManager.remove(any()) }
    }

    @Test
    @DisplayName("禁用时不应调用持久化监听器")
    fun testPersistListenersDisabled() {
        // Given
        val unitOfWork = TestableJpaUnitOfWork(
            uowInterceptors = emptyList(),
            persistListenerManager = persistListenerManager,
            supportEntityInlinePersistListener = false,
        )
        unitOfWork.setTestEntityManager(entityManager)
        JpaUnitOfWork.fixAopWrapper(unitOfWork)

        val entity = TestEntity(1L, "test")
        unitOfWork.persist(entity, PersistIntent.CREATE)

        // When
        unitOfWork.save()

        // Then
        verify(exactly = 0) { persistListenerManager.onChange(any(), any()) }
    }

    @Test
    @DisplayName("应正确处理AOP包装器设置")
    fun testFixAopWrapper() {
        // Given
        val newUnitOfWork = TestableJpaUnitOfWork(
            uowInterceptors = emptyList(),
            persistListenerManager = persistListenerManager,
            supportEntityInlinePersistListener = false,
        )

        // When
        JpaUnitOfWork.fixAopWrapper(newUnitOfWork)

        // Then
        assertSame(newUnitOfWork, JpaUnitOfWork.instance)
    }

    @Test
    @DisplayName("CREATE intent with preassigned application-side id should not query existence")
    fun createIntentWithPreassignedApplicationSideIdShouldNotQueryExistence() {
        val entity = ApplicationSideLongEntity(id = 100L, name = "new")
        every { mockEntityInfo.isNew(entity) } returns false
        every { mockEntityInfo.getId(entity) } returns 100L

        jpaUnitOfWork.persist(entity, PersistIntent.CREATE)
        jpaUnitOfWork.save()

        verify { entityManager.persist(entity) }
        verify { persistListenerManager.onChange(entity, PersistType.CREATE) }
        verify(exactly = 0) { entityManager.find(ApplicationSideLongEntity::class.java, any()) }
        verify(exactly = 0) { entityManager.merge(entity) }
    }

    @Test
    @DisplayName("application-side id should be assigned before beforeTransaction interceptors")
    fun applicationSideIdShouldBeAssignedBeforeBeforeTransactionInterceptors() {
        val entity = ApplicationSideLongEntity(id = 0L, name = "allocated")
        every { mockEntityInfo.isNew(entity) } returns true

        jpaUnitOfWork.persist(entity, PersistIntent.CREATE)
        jpaUnitOfWork.save()

        verify {
            interceptor1.beforeTransaction(
                match<Set<Any>> { persisted -> (persisted.single() as ApplicationSideLongEntity).id == 1001L },
                any()
            )
        }
    }

    @Test
    @DisplayName("existing intent with application-side id should merge without querying existence or reporting update")
    fun existingIntentWithApplicationSideIdShouldMergeWithoutQueryingExistenceOrReportingUpdate() {
        val entity = ApplicationSideLongEntity(id = 100L, name = "existing")
        every { mockEntityInfo.isNew(entity) } returns false
        every { mockEntityInfo.getId(entity) } returns 100L
        jpaUnitOfWork.observeRepositoryLoad(entity, AggregateLoadPlan.WHOLE_AGGREGATE)

        jpaUnitOfWork.persist(entity)
        jpaUnitOfWork.save()

        verify { entityManager.merge(entity) }
        verify(exactly = 0) { persistListenerManager.onChange(entity, PersistType.UPDATE) }
        verify(exactly = 0) { entityManager.find(ApplicationSideLongEntity::class.java, any()) }
        verify(exactly = 0) { entityManager.persist(entity) }
    }

    @Test
    @DisplayName("different instances with same identity should fail before flush")
    fun differentInstancesWithSameIdentityShouldFailBeforeFlush() {
        val first = TestEntity(7L, "first")
        val second = TestEntity(7L, "second")
        every { mockEntityInfo.isNew(first) } returns false
        every { mockEntityInfo.isNew(second) } returns false
        every { mockEntityInfo.getId(first) } returns 7L
        every { mockEntityInfo.getId(second) } returns 7L
        every { entityManager.contains(first) } returns true

        jpaUnitOfWork.persist(first)
        jpaUnitOfWork.remove(second)

        val error = assertThrows(IllegalStateException::class.java) {
            jpaUnitOfWork.save()
        }

        assertTrue(error.message!!.contains("conflicting UnitOfWork registrations"))
        verify(exactly = 0) { entityManager.flush() }
    }

    @Test
    @DisplayName("preflight conflict failure should clear processing entities")
    fun preflightConflictFailureShouldClearProcessingEntities() {
        val first = TestEntity(8L, "first")
        val second = TestEntity(8L, "second")
        every { mockEntityInfo.isNew(first) } returns false
        every { mockEntityInfo.isNew(second) } returns false
        every { mockEntityInfo.getId(first) } returns 8L
        every { mockEntityInfo.getId(second) } returns 8L
        every { entityManager.contains(first) } returns true

        jpaUnitOfWork.persist(first)
        jpaUnitOfWork.remove(second)

        assertThrows(IllegalStateException::class.java) {
            jpaUnitOfWork.save()
        }

        assertEquals(0, processingEntityCount())
    }

    @Test
    @DisplayName("three argument JpaUnitOfWork constructor should remain callable")
    fun threeArgumentJpaUnitOfWorkConstructorShouldRemainCallable() {
        val unitOfWork = JpaUnitOfWork(
            uowInterceptors,
            persistListenerManager,
            supportEntityInlinePersistListener = true,
        )
        val constructor = JpaUnitOfWork::class.java.getConstructor(
            List::class.java,
            PersistListenerManager::class.java,
            Boolean::class.javaPrimitiveType,
        )

        assertEquals(JpaUnitOfWork::class.java, unitOfWork.javaClass)
        assertEquals(JpaUnitOfWork::class.java, constructor.declaringClass)
    }

    // Test helper classes
    @jakarta.persistence.Entity
    data class TestEntity(
        @jakarta.persistence.Id
        val id: Long?,
        val name: String
    )

    private class FixedLongStrategy : IdentifierStrategy {
        override val name: String = BuiltInIdentifierStrategies.SNOWFLAKE
        override val capabilities: Set<IdentifierCapability> = setOf(IdentifierCapability.ENTITY_ID_PREASSIGNMENT)
        override fun supports(type: KClass<*>): Boolean = type == Long::class
        override fun <T : Any> next(type: KClass<T>): T {
            require(supports(type)) { "identifier strategy $name does not support output type ${type.qualifiedName}" }
            @Suppress("UNCHECKED_CAST")
            return 1001L as T
        }
        override fun isDefaultValue(value: Any?, type: KClass<*>): Boolean = value == null || value == 0L
    }

    private class ApplicationSideLongEntity(
        @field:Id
        @field:ApplicationSideId(strategy = "snowflake")
        var id: Long = 0L,
        var name: String = ""
    )

    private class ObservedRoot(
        @field:Id
        var id: Long? = null,
        @field:OneToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE], orphanRemoval = true)
        @field:JoinColumn(name = "root_id")
        val children: MutableList<ObservedChild> = mutableListOf(),
    )

    private class ObservedChild(
        @field:Id
        var id: Long? = null,
    )

    @Embeddable
    class TestStrongEntityId protected constructor() : StrongId, Serializable {
        @Column(name = "value", nullable = false, updatable = false, length = 36)
        override lateinit var value: String
            protected set

        constructor(value: String) : this() {
            this.value = value
        }

        companion object {
            fun new(): TestStrongEntityId = TestStrongEntityId("018f0000-0000-7000-8000-000000000001")
        }
    }

    @jakarta.persistence.Entity
    class StrongRootEntity {
        @EmbeddedId
        lateinit var id: TestStrongEntityId

        @OneToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE], orphanRemoval = true)
        @JoinColumn(name = "root_id", nullable = false)
        val children: MutableList<StrongChildEntity> = mutableListOf()
    }

    @jakarta.persistence.Entity
    class StrongChildEntity {
        @EmbeddedId
        lateinit var id: TestStrongEntityId
    }

}
