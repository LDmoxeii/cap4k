package com.only4.cap4k.ddd.application

import com.only4.cap4k.ddd.core.application.PersistIntent
import com.only4.cap4k.ddd.core.application.UnitOfWorkInterceptor
import com.only4.cap4k.ddd.core.domain.id.GeneratedOwnIdAccessor
import com.only4.cap4k.ddd.core.domain.id.GeneratedOwnIdCatalog
import com.only4.cap4k.ddd.core.domain.id.GeneratedOwnIdRegistry
import com.only4.cap4k.ddd.core.domain.id.MapBackedGeneratedOwnIdRegistry
import com.only4.cap4k.ddd.core.domain.id.StrongId
import com.only4.cap4k.ddd.core.domain.id.readInitializedOrNull
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
import org.hibernate.proxy.HibernateProxy
import org.hibernate.proxy.LazyInitializer
import org.springframework.transaction.annotation.Propagation
import java.io.Serializable

@DisplayName("JpaUnitOfWork 测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class JpaUnitOfWorkTest {

    private lateinit var entityManager: EntityManager
    private lateinit var persistListenerManager: PersistListenerManager
    private lateinit var uowInterceptors: List<UnitOfWorkInterceptor>
    private lateinit var interceptor1: UnitOfWorkInterceptor
    private lateinit var interceptor2: UnitOfWorkInterceptor
    private lateinit var jpaUnitOfWork: TestableJpaUnitOfWork
    private lateinit var generatedOwnIdRegistry: GeneratedOwnIdRegistry
    private lateinit var strongRootEntityAccessor: StrongRootEntityAccessor
    private lateinit var strongChildEntityAccessor: StrongChildEntityAccessor
    private lateinit var mockEntityInfo: org.springframework.data.jpa.repository.support.JpaEntityInformation<Any, Any>

    // Testable subclass to access protected members
    class TestableJpaUnitOfWork(
        uowInterceptors: List<UnitOfWorkInterceptor>,
        persistListenerManager: PersistListenerManager,
        supportEntityInlinePersistListener: Boolean,
        generatedOwnIdRegistry: GeneratedOwnIdRegistry = MapBackedGeneratedOwnIdRegistry(emptyList()),
        private val dirtyExistingEntities: Set<Any> = emptySet(),
    ) : JpaUnitOfWork(
        uowInterceptors,
        persistListenerManager,
        supportEntityInlinePersistListener,
        generatedOwnIdRegistry
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

        strongRootEntityAccessor = StrongRootEntityAccessor()
        strongChildEntityAccessor = StrongChildEntityAccessor()
        val generatedOwnIdCatalog = object : GeneratedOwnIdCatalog {
            override val accessors: List<GeneratedOwnIdAccessor<*, *>> = listOf(
                strongRootEntityAccessor,
                strongChildEntityAccessor,
            )
        }
        generatedOwnIdRegistry = MapBackedGeneratedOwnIdRegistry(listOf(generatedOwnIdCatalog))
        jpaUnitOfWork = TestableJpaUnitOfWork(
            uowInterceptors = uowInterceptors,
            persistListenerManager = persistListenerManager,
            supportEntityInlinePersistListener = true,
            generatedOwnIdRegistry = generatedOwnIdRegistry,
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
    @DisplayName("repository observation distinguishes root from generated owned child")
    fun repositoryObservationShouldDistinguishRootFromGeneratedOwnedChild() {
        val child = ObservedChild(20L)
        val root = ObservedRoot(10L, mutableListOf(child))
        every { mockEntityInfo.isNew(root) } returns false
        every { mockEntityInfo.getId(root) } returns 10L
        every { mockEntityInfo.isNew(child) } returns false
        every { mockEntityInfo.getId(child) } returns 20L

        jpaUnitOfWork.observeRepositoryLoad(root, AggregateLoadPlan.WHOLE_AGGREGATE)

        val baseline = jpaUnitOfWork.observedRepositoryBaseline()
        assertTrue(baseline.isObservedRoot(root))
        assertFalse(baseline.isObservedChild(root))
        org.junit.jupiter.api.Assertions.assertNull(baseline.observedRootForChild(root))
        assertTrue(baseline.isObservedChild(child))
        assertSame(root, baseline.observedRootForChild(child))
        assertSame(root, baseline.observedRootFor(child))
    }

    @Test
    @DisplayName("persist rejects a repository-observed owned child as a standalone target")
    fun persistShouldRejectRepositoryObservedOwnedChild() {
        val child = ObservedChild(20L)
        val root = ObservedRoot(10L, mutableListOf(child))
        every { mockEntityInfo.isNew(root) } returns false
        every { mockEntityInfo.getId(root) } returns root.id
        every { mockEntityInfo.isNew(child) } returns false
        every { mockEntityInfo.getId(child) } returns child.id
        jpaUnitOfWork.observeRepositoryLoad(root, AggregateLoadPlan.WHOLE_AGGREGATE)

        val error = assertThrows(IllegalStateException::class.java) {
            jpaUnitOfWork.persist(child)
        }

        assertTrue(error.message!!.contains("persist the aggregate root"))
        assertTrue(error.message!!.contains(ObservedRoot::class.java.name))
        assertTrue(error.message!!.contains(ObservedChild::class.java.name))
        verify(exactly = 0) { entityManager.persist(any()) }
        verify(exactly = 0) { entityManager.merge<Any>(any()) }
        verify(exactly = 0) { entityManager.flush() }
    }

    @Test
    @DisplayName("persist rejects a Hibernate proxy alias of a repository-observed owned child")
    fun persistShouldRejectHibernateProxyAliasOfRepositoryObservedOwnedChild() {
        val implementation = ObservedChild(20L)
        val root = ObservedRoot(10L, mutableListOf(implementation))
        val proxy = hibernateProxy(ObservedChild::class.java, implementation.id, implementation)
        every { mockEntityInfo.isNew(root) } returns false
        every { mockEntityInfo.getId(root) } returns root.id
        every { mockEntityInfo.isNew(implementation) } returns false
        every { mockEntityInfo.getId(implementation) } returns implementation.id
        every { mockEntityInfo.isNew(proxy) } returns false
        every { mockEntityInfo.getId(proxy) } returns implementation.id
        jpaUnitOfWork.observeRepositoryLoad(root, AggregateLoadPlan.WHOLE_AGGREGATE)

        val error = assertThrows(IllegalStateException::class.java) {
            jpaUnitOfWork.persist(proxy)
        }

        assertTrue(error.message!!.contains("persist the aggregate root"))
        assertTrue(error.message!!.contains(ObservedRoot::class.java.name))
        assertTrue(error.message!!.contains(ObservedChild::class.java.name))
    }

    @Test
    @DisplayName("remove rejects a repository-observed owned child as a standalone target")
    fun removeShouldRejectRepositoryObservedOwnedChild() {
        val child = ObservedChild(20L)
        val root = ObservedRoot(10L, mutableListOf(child))
        every { mockEntityInfo.isNew(root) } returns false
        every { mockEntityInfo.getId(root) } returns root.id
        every { mockEntityInfo.isNew(child) } returns false
        every { mockEntityInfo.getId(child) } returns child.id
        jpaUnitOfWork.observeRepositoryLoad(root, AggregateLoadPlan.WHOLE_AGGREGATE)

        val error = assertThrows(IllegalStateException::class.java) {
            jpaUnitOfWork.remove(child)
        }

        assertTrue(error.message!!.contains("persist the aggregate root"))
        assertTrue(error.message!!.contains(ObservedRoot::class.java.name))
        assertTrue(error.message!!.contains(ObservedChild::class.java.name))
        verify(exactly = 0) { entityManager.remove(any()) }
        verify(exactly = 0) { entityManager.merge<Any>(any()) }
        verify(exactly = 0) { entityManager.flush() }
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
    @DisplayName("default EXISTING persist rejects an unobserved detached root clone with an observed identity")
    fun defaultPersistShouldRejectUnobservedDetachedRootCloneWithObservedIdentity() {
        val observed = TestEntity(1L, "observed")
        val clone = TestEntity(1L, "clone")
        every { mockEntityInfo.isNew(observed) } returns false
        every { mockEntityInfo.getId(observed) } returns observed.id
        every { mockEntityInfo.isNew(clone) } returns false
        every { mockEntityInfo.getId(clone) } returns clone.id
        every { entityManager.contains(clone) } returns false
        jpaUnitOfWork.observeRepositoryLoad(observed, AggregateLoadPlan.WHOLE_AGGREGATE)

        val error = assertThrows(IllegalStateException::class.java) {
            jpaUnitOfWork.persist(clone)
        }

        assertTrue(error.message!!.contains("detached unobserved instances cannot be merged safely"))
        verify(exactly = 0) { entityManager.merge<Any>(any()) }
        verify(exactly = 0) { entityManager.persist(any()) }
        verify(exactly = 0) { entityManager.flush() }
    }

    @Test
    @DisplayName("default EXISTING persist rejects an assigned detached strong entity without trustworthy evidence")
    fun defaultPersistShouldRejectAssignedDetachedStrongEntityWithoutBaseline() {
        val assignedId = TestStrongEntityId("018f0000-0000-7000-8000-000000000100")
        val entity = StrongRootEntity().also { it.id = assignedId }
        every { mockEntityInfo.isNew(entity) } returns false
        every { mockEntityInfo.getId(entity) } returns assignedId
        every { entityManager.contains(entity) } returns false

        val error = assertThrows(IllegalStateException::class.java) {
            jpaUnitOfWork.persist(entity)
        }

        assertTrue(error.message!!.contains("repository observation baseline or provider-managed existing state"))
        verify(exactly = 0) { entityManager.merge(entity) }
        verify(exactly = 0) { entityManager.persist(entity) }
        verify(exactly = 0) { entityManager.flush() }
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
    fun `CREATE persist completes registered root before returning`() {
        val root = StrongRootEntity()

        jpaUnitOfWork.persist(root, PersistIntent.CREATE)

        assertEquals("018f0000-0000-7000-8000-000000000001", root.id.value)
        assertEquals(1, strongRootEntityAccessor.nextCalls)
    }

    @Test
    fun `CREATE persist completes every reachable registered child before returning`() {
        val root = StrongRootEntity().also {
            it.children += StrongChildEntity()
            it.children += StrongChildEntity()
        }

        jpaUnitOfWork.persist(root, PersistIntent.CREATE)

        assertTrue(root.children.all { child -> strongChildEntityAccessor.current(child) != null })
        assertEquals(2, strongChildEntityAccessor.nextCalls)
    }

    @Test
    fun `CREATE persist preserves preassigned registered ids`() {
        val rootId = TestStrongEntityId("018f0000-0000-7000-8000-000000000099")
        val childId = TestStrongEntityId("018f0000-0000-7000-8000-000000000098")
        val child = StrongChildEntity().also { it.id = childId }
        val root = StrongRootEntity().also {
            it.id = rootId
            it.children += child
        }

        jpaUnitOfWork.persist(root, PersistIntent.CREATE)

        assertSame(rootId, root.id)
        assertSame(childId, child.id)
        assertEquals(0, strongRootEntityAccessor.nextCalls)
        assertEquals(0, strongChildEntityAccessor.nextCalls)
    }

    @Test
    fun `EXISTING persist preserves observed root and child ids`() {
        val rootId = TestStrongEntityId("018f0000-0000-7000-8000-000000000099")
        val childId = TestStrongEntityId("018f0000-0000-7000-8000-000000000098")
        val child = StrongChildEntity().also { it.id = childId }
        val root = StrongRootEntity().also {
            it.id = rootId
            it.children += child
        }
        every { mockEntityInfo.isNew(root) } returns false
        every { mockEntityInfo.getId(root) } returns rootId
        every { mockEntityInfo.isNew(child) } returns false
        every { mockEntityInfo.getId(child) } returns childId
        jpaUnitOfWork.observeRepositoryLoad(root, AggregateLoadPlan.WHOLE_AGGREGATE)

        jpaUnitOfWork.persist(root, PersistIntent.EXISTING)

        assertSame(rootId, root.id)
        assertSame(childId, child.id)
        assertEquals(0, strongRootEntityAccessor.nextCalls)
        assertEquals(0, strongChildEntityAccessor.nextCalls)
    }

    @Test
    fun `EXISTING persist completes only newly reachable registered children`() {
        val rootId = TestStrongEntityId("018f0000-0000-7000-8000-000000000099")
        val observedChildId = TestStrongEntityId("018f0000-0000-7000-8000-000000000098")
        val observedChild = StrongChildEntity().also { it.id = observedChildId }
        val root = StrongRootEntity().also {
            it.id = rootId
            it.children += observedChild
        }
        every { mockEntityInfo.isNew(root) } returns false
        every { mockEntityInfo.getId(root) } returns rootId
        every { mockEntityInfo.isNew(observedChild) } returns false
        every { mockEntityInfo.getId(observedChild) } returns observedChildId
        jpaUnitOfWork.observeRepositoryLoad(root, AggregateLoadPlan.WHOLE_AGGREGATE)
        val newChild = StrongChildEntity().also(root.children::add)

        jpaUnitOfWork.persist(root, PersistIntent.EXISTING)

        assertSame(rootId, root.id)
        assertSame(observedChildId, observedChild.id)
        assertTrue(strongChildEntityAccessor.current(newChild) != null)
        assertEquals(0, strongRootEntityAccessor.nextCalls)
        assertEquals(1, strongChildEntityAccessor.nextCalls)
    }

    @Test
    fun `completion is idempotent across persist and save`() {
        val child = StrongChildEntity()
        val root = StrongRootEntity().also { it.children += child }

        jpaUnitOfWork.persist(root, PersistIntent.CREATE)
        assertEquals(1, strongRootEntityAccessor.nextCalls)
        assertEquals(1, strongChildEntityAccessor.nextCalls)

        jpaUnitOfWork.save()

        assertEquals(1, strongRootEntityAccessor.nextCalls)
        assertEquals(1, strongChildEntityAccessor.nextCalls)
    }

    @Test
    fun `unregistered database identity entity remains provider managed`() {
        val entity = TestEntity(null, "provider-managed")

        jpaUnitOfWork.persist(entity, PersistIntent.CREATE)

        assertEquals(null, entity.id)
        jpaUnitOfWork.save()
        verify { entityManager.persist(entity) }
    }

    @Test
    @DisplayName("CREATE assignment is visible to beforeTransaction interceptors")
    fun createPersistShouldExposeGeneratedStrongRootIdToBeforeTransactionInterceptors() {
        val entity = StrongRootEntity()
        val interceptedIds = mutableListOf<TestStrongEntityId>()
        every { interceptor1.beforeTransaction(any(), any()) } answers {
            interceptedIds += entity.id
        }

        jpaUnitOfWork.persist(entity, PersistIntent.CREATE)
        jpaUnitOfWork.save()

        assertEquals("018f0000-0000-7000-8000-000000000001", entity.id.value)
        assertEquals(listOf(entity.id), interceptedIds)
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
    fun `root first pending child is absorbed into the root entry`() {
        val root = StrongRootEntity()
        val child = StrongChildEntity()
        root.children += child

        jpaUnitOfWork.persist(root, PersistIntent.CREATE)
        jpaUnitOfWork.persist(child, PersistIntent.CREATE)
        jpaUnitOfWork.save()

        verify { entityManager.persist(root) }
        verify(exactly = 0) { entityManager.persist(child) }
        verify {
            interceptor1.beforeTransaction(
                match<Set<Any>> { it.size == 1 && it.single() === root },
                emptySet(),
            )
        }
        verify(exactly = 0) { persistListenerManager.onChange(child, any()) }
        assertTrue(child.hasAssignedId())
    }

    @Test
    fun `child first registration converges to the same root only entry`() {
        val root = StrongRootEntity()
        val child = StrongChildEntity()
        root.children += child

        jpaUnitOfWork.persist(child, PersistIntent.CREATE)
        jpaUnitOfWork.persist(root, PersistIntent.CREATE)
        jpaUnitOfWork.save()

        verify { entityManager.persist(root) }
        verify(exactly = 0) { entityManager.persist(child) }
        verify {
            interceptor1.beforeTransaction(
                match<Set<Any>> { it.size == 1 && it.single() === root },
                emptySet(),
            )
        }
    }

    @Test
    fun `nested pending owned entries retain only the outermost root`() {
        val root = StrongRootEntity()
        val child = StrongChildEntity()
        val grandchild = StrongChildEntity()
        root.children += child
        child.children += grandchild

        jpaUnitOfWork.persist(grandchild, PersistIntent.CREATE)
        jpaUnitOfWork.persist(child, PersistIntent.CREATE)
        jpaUnitOfWork.persist(root, PersistIntent.CREATE)
        jpaUnitOfWork.save()

        verify { entityManager.persist(root) }
        verify(exactly = 0) { entityManager.persist(child) }
        verify(exactly = 0) { entityManager.persist(grandchild) }
        verify {
            interceptor1.beforeTransaction(
                match<Set<Any>> { it.size == 1 && it.single() === root },
                emptySet(),
            )
        }
        assertTrue(child.hasAssignedId())
        assertTrue(grandchild.hasAssignedId())
    }

    @Test
    fun `pending child shared by unrelated roots fails deterministically in registration order`() {
        val child = StrongChildEntity()
        val firstRoot = StrongRootEntity().also { it.children += child }
        val secondRoot = StrongRootEntity().also { it.children += child }

        jpaUnitOfWork.persist(child, PersistIntent.CREATE)
        jpaUnitOfWork.persist(firstRoot, PersistIntent.CREATE)
        jpaUnitOfWork.persist(secondRoot, PersistIntent.CREATE)

        val error = assertThrows(IllegalStateException::class.java) { jpaUnitOfWork.save() }

        assertTrue(error.message!!.contains("multiple unrelated pending roots"))
        assertTrue(error.message!!.contains(StrongChildEntity::class.java.name))
        assertTrue(error.message!!.contains(StrongRootEntity::class.java.name))
        verify(exactly = 0) { entityManager.persist(any()) }
        verify(exactly = 0) { entityManager.flush() }
    }

    @Test
    fun `pending child shared by unrelated roots fails with reversed root registration`() {
        val child = StrongChildEntity()
        val firstRoot = StrongRootEntity().also { it.children += child }
        val secondRoot = StrongRootEntity().also { it.children += child }

        jpaUnitOfWork.persist(child, PersistIntent.CREATE)
        jpaUnitOfWork.persist(secondRoot, PersistIntent.CREATE)
        jpaUnitOfWork.persist(firstRoot, PersistIntent.CREATE)

        val error = assertThrows(IllegalStateException::class.java) { jpaUnitOfWork.save() }

        assertTrue(error.message!!.contains("multiple unrelated pending roots"))
        assertTrue(error.message!!.contains(StrongChildEntity::class.java.name))
        assertTrue(error.message!!.contains(StrongRootEntity::class.java.name))
        verify(exactly = 0) { entityManager.persist(any()) }
        verify(exactly = 0) { entityManager.flush() }
    }

    @Test
    fun `isolated CREATE with no pending owner remains caller declared top level`() {
        val child = StrongChildEntity()

        jpaUnitOfWork.persist(child, PersistIntent.CREATE)
        jpaUnitOfWork.save()

        verify { entityManager.persist(child) }
    }

    @Test
    fun `remove rejects a pending owned child while its aggregate root is pending`() {
        val root = StrongRootEntity()
        val child = StrongChildEntity()
        root.children += child
        jpaUnitOfWork.persist(root, PersistIntent.CREATE)
        jpaUnitOfWork.remove(child)

        val error = assertThrows(IllegalStateException::class.java) { jpaUnitOfWork.save() }

        assertTrue(
            error.message!!.contains(
                "UnitOfWork.remove cannot register an owned child while its aggregate root is pending"
            )
        )
        assertTrue(error.message!!.contains(StrongChildEntity::class.java.name))
        verify(exactly = 0) { entityManager.persist(any()) }
        verify(exactly = 0) { entityManager.flush() }
    }

    @Test
    fun `reconciliation failure clears same-thread repository observation state`() {
        val observedChild = StrongChildEntity().also {
            it.id = TestStrongEntityId("018f0000-0000-7000-8000-000000000098")
        }
        val observedRoot = StrongRootEntity().also {
            it.id = TestStrongEntityId("018f0000-0000-7000-8000-000000000099")
            it.children += observedChild
        }
        every { mockEntityInfo.isNew(observedRoot) } returns false
        every { mockEntityInfo.getId(observedRoot) } returns observedRoot.id
        every { mockEntityInfo.isNew(observedChild) } returns false
        every { mockEntityInfo.getId(observedChild) } returns observedChild.id
        jpaUnitOfWork.observeRepositoryLoad(observedRoot, AggregateLoadPlan.WHOLE_AGGREGATE)

        val sharedChild = StrongChildEntity()
        val firstRoot = StrongRootEntity().also { it.children += sharedChild }
        val secondRoot = StrongRootEntity().also { it.children += sharedChild }
        jpaUnitOfWork.persist(firstRoot, PersistIntent.CREATE)
        jpaUnitOfWork.persist(secondRoot, PersistIntent.CREATE)
        jpaUnitOfWork.persist(sharedChild, PersistIntent.CREATE)

        val error = assertThrows(IllegalStateException::class.java) {
            jpaUnitOfWork.save()
        }

        assertTrue(error.message!!.contains("multiple unrelated pending roots"))
        assertAll(
            {
                assertFalse(
                    jpaUnitOfWork.observedRepositoryBaseline().hasBaselineFor(observedRoot)
                )
            },
            {
                assertDoesNotThrow {
                    jpaUnitOfWork.persist(observedChild, PersistIntent.CREATE)
                }
            },
        )
    }

    @Test
    @DisplayName("save absorbs a Hibernate proxy alias of a pending owned child")
    fun saveShouldAbsorbHibernateProxyAliasOfPendingOwnedChild() {
        val implementation = ObservedChild(20L)
        val root = ObservedRoot(10L, mutableListOf(implementation))
        val proxy = hibernateProxy(ObservedChild::class.java, implementation.id, implementation)
        every { mockEntityInfo.isNew(implementation) } returns false
        every { mockEntityInfo.getId(implementation) } returns implementation.id
        every { mockEntityInfo.isNew(proxy) } returns false
        every { mockEntityInfo.getId(proxy) } returns implementation.id

        jpaUnitOfWork.persist(root, PersistIntent.CREATE)
        jpaUnitOfWork.persist(proxy, PersistIntent.CREATE)
        jpaUnitOfWork.save()

        verify { entityManager.persist(root) }
        verify(exactly = 0) { entityManager.persist(proxy) }
        verify {
            interceptor1.beforeTransaction(
                match<Set<Any>> { it.size == 1 && it.single() === root },
                emptySet(),
            )
        }
    }

    @Test
    @DisplayName("save rejects a pending standalone child added to its root by preInTransaction")
    fun saveShouldRejectPendingStandaloneChildAddedToRootByPreInTransaction() {
        val root = StrongRootEntity()
        val child = StrongChildEntity()
        every { interceptor1.preInTransaction(any(), any()) } answers {
            root.children += child
        }

        jpaUnitOfWork.persist(root, PersistIntent.CREATE)
        jpaUnitOfWork.persist(child, PersistIntent.CREATE)

        val error = assertThrows(IllegalStateException::class.java) {
            jpaUnitOfWork.save()
        }

        assertTrue(
            error.message!!.contains(
                "pending ownership changed after UnitOfWork interceptor input was constructed"
            )
        )
        verify(exactly = 0) { entityManager.persist(any()) }
        verify(exactly = 0) { entityManager.flush() }
    }

    @Test
    @DisplayName("root-only existing enrollment with a new owned child remains valid")
    fun existingRootOnlyEnrollmentWithNewOwnedChildShouldRemainValid() {
        val root = StrongRootEntity().also {
            it.id = TestStrongEntityId("018f0000-0000-7000-8000-000000000088")
        }
        val observedChild = StrongChildEntity().also {
            it.id = TestStrongEntityId("018f0000-0000-7000-8000-000000000087")
        }
        root.children += observedChild
        every { mockEntityInfo.isNew(root) } returns false
        every { mockEntityInfo.getId(root) } returns root.id
        every { mockEntityInfo.isNew(observedChild) } returns false
        every { mockEntityInfo.getId(observedChild) } returns observedChild.id
        jpaUnitOfWork.observeRepositoryLoad(root, AggregateLoadPlan.WHOLE_AGGREGATE)

        val newChild = StrongChildEntity()
        root.children += newChild
        jpaUnitOfWork.persist(root)
        jpaUnitOfWork.save()

        assertEquals("018f0000-0000-7000-8000-000000000001", newChild.id.value)
        verify { entityManager.merge(root) }
        verify { entityManager.flush() }
        verify(exactly = 0) { entityManager.persist(newChild) }
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

        assertTrue(error.message!!.contains("missing generated own ID"))
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
            generatedOwnIdRegistry = generatedOwnIdRegistry,
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
            generatedOwnIdRegistry = generatedOwnIdRegistry,
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
    @DisplayName("CREATE intent preserves a preassigned strong id without querying existence")
    fun createIntentWithPreassignedStrongIdShouldNotQueryExistence() {
        val preassignedId = TestStrongEntityId("018f0000-0000-7000-8000-000000000100")
        val entity = StrongRootEntity().also { it.id = preassignedId }
        every { mockEntityInfo.isNew(entity) } returns false
        every { mockEntityInfo.getId(entity) } returns preassignedId

        jpaUnitOfWork.persist(entity, PersistIntent.CREATE)
        jpaUnitOfWork.save()

        assertSame(preassignedId, entity.id)
        verify { entityManager.persist(entity) }
        verify { persistListenerManager.onChange(entity, PersistType.CREATE) }
        verify(exactly = 0) { entityManager.find(StrongRootEntity::class.java, any()) }
        verify(exactly = 0) { entityManager.merge(entity) }
    }

    @Test
    @DisplayName("observed EXISTING strong id merges without querying existence or reporting update")
    fun observedExistingStrongIdShouldMergeWithoutQueryingExistenceOrReportingUpdate() {
        val entity = StrongRootEntity().also {
            it.id = TestStrongEntityId("018f0000-0000-7000-8000-000000000101")
        }
        every { mockEntityInfo.isNew(entity) } returns false
        every { mockEntityInfo.getId(entity) } returns entity.id
        jpaUnitOfWork.observeRepositoryLoad(entity, AggregateLoadPlan.WHOLE_AGGREGATE)

        jpaUnitOfWork.persist(entity)
        jpaUnitOfWork.save()

        verify { entityManager.merge(entity) }
        verify(exactly = 0) { persistListenerManager.onChange(entity, PersistType.UPDATE) }
        verify(exactly = 0) { entityManager.find(StrongRootEntity::class.java, any()) }
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
    @DisplayName("default registry keeps the three argument Kotlin call site callable")
    fun defaultRegistryShouldKeepThreeArgumentKotlinCallSiteCallable() {
        val unitOfWork = JpaUnitOfWork(
            uowInterceptors,
            persistListenerManager,
            supportEntityInlinePersistListener = true,
        )

        assertEquals(JpaUnitOfWork::class.java, unitOfWork.javaClass)
    }

    // Test helper classes
    @jakarta.persistence.Entity
    data class TestEntity(
        @jakarta.persistence.Id
        val id: Long?,
        val name: String
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

    private fun hibernateProxy(
        entityType: Class<*>,
        id: Any?,
        implementation: Any,
    ): HibernateProxy {
        val initializer = mockk<LazyInitializer>()
        every { initializer.persistentClass } returns entityType
        every { initializer.implementationClass } returns entityType
        every { initializer.identifier } returns id
        every { initializer.implementation } returns implementation
        every { initializer.isUninitialized } returns false
        val proxy = mockk<HibernateProxy>()
        every { proxy.asHibernateProxy() } returns proxy
        every { proxy.hibernateLazyInitializer } returns initializer
        return proxy
    }

    @Embeddable
    class TestStrongEntityId protected constructor() : StrongId<String>, Serializable {
        @Column(name = "value", nullable = false, updatable = false, length = 36)
        override lateinit var value: String
            protected set

        constructor(value: String) : this() {
            this.value = value
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

        @OneToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE], orphanRemoval = true)
        @JoinColumn(name = "parent_id", nullable = false)
        val children: MutableList<StrongChildEntity> = mutableListOf()

        fun hasAssignedId(): Boolean = this::id.isInitialized
    }

    private class StrongRootEntityAccessor : GeneratedOwnIdAccessor<StrongRootEntity, TestStrongEntityId> {
        override val entityType = StrongRootEntity::class
        override val label = "StrongRootEntity.id"
        var nextCalls = 0

        override fun current(entity: StrongRootEntity): TestStrongEntityId? =
            readInitializedOrNull { entity.id }

        override fun assign(entity: StrongRootEntity, id: TestStrongEntityId) {
            entity.id = id
        }

        override fun next(): TestStrongEntityId = sequentialUuid7((++nextCalls).toLong())
    }

    private class StrongChildEntityAccessor : GeneratedOwnIdAccessor<StrongChildEntity, TestStrongEntityId> {
        override val entityType = StrongChildEntity::class
        override val label = "StrongChildEntity.id"
        var nextCalls = 0

        override fun current(entity: StrongChildEntity): TestStrongEntityId? =
            readInitializedOrNull { entity.id }

        override fun assign(entity: StrongChildEntity, id: TestStrongEntityId) {
            entity.id = id
        }

        override fun next(): TestStrongEntityId = sequentialUuid7((++nextCalls).toLong())
    }

    companion object {
        private fun sequentialUuid7(sequence: Long): TestStrongEntityId =
            TestStrongEntityId("018f0000-0000-7000-8000-${sequence.toString(16).padStart(12, '0')}")
    }

}
