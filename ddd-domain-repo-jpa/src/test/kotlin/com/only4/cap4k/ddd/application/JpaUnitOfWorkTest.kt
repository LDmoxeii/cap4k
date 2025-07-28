package com.only4.cap4k.ddd.application

import com.only4.cap4k.ddd.core.application.UnitOfWorkInterceptor
import com.only4.cap4k.ddd.core.domain.aggregate.Aggregate
import com.only4.cap4k.ddd.core.domain.aggregate.ValueObject
import com.only4.cap4k.ddd.core.domain.repo.PersistListenerManager
import io.mockk.*
import jakarta.persistence.EntityManager
import org.hibernate.engine.spi.PersistenceContext
import org.hibernate.engine.spi.SessionImplementor
import org.junit.jupiter.api.*
import org.springframework.transaction.annotation.Propagation
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@DisplayName("JpaUnitOfWork 测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class JpaUnitOfWorkTest {

    private lateinit var entityManager: EntityManager
    private lateinit var persistListenerManager: PersistListenerManager
    private lateinit var uowInterceptors: List<UnitOfWorkInterceptor>
    private lateinit var interceptor1: UnitOfWorkInterceptor
    private lateinit var interceptor2: UnitOfWorkInterceptor
    private lateinit var jpaUnitOfWork: TestableJpaUnitOfWork
    private lateinit var sessionImplementor: SessionImplementor
    private lateinit var persistenceContext: PersistenceContext
    private lateinit var mockEntityInfo: org.springframework.data.jpa.repository.support.JpaEntityInformation<Any, Any>

    // Testable subclass to access protected members
    class TestableJpaUnitOfWork(
        uowInterceptors: List<UnitOfWorkInterceptor>,
        persistListenerManager: PersistListenerManager,
        supportEntityInlinePersistListener: Boolean,
        supportValueObjectExistsCheckOnSave: Boolean
    ) : JpaUnitOfWork(
        uowInterceptors,
        persistListenerManager,
        supportEntityInlinePersistListener,
        supportValueObjectExistsCheckOnSave
    ) {

        fun setTestEntityManager(em: EntityManager) {
            this.entityManager = em
        }

        fun testPersistenceContextEntities() = persistenceContextEntities()
    }

    @BeforeEach
    fun setUp() {
        // Create fresh mocks for each test
        entityManager = mockk()
        persistListenerManager = mockk(relaxed = true)
        interceptor1 = mockk(relaxed = true)
        interceptor2 = mockk(relaxed = true)
        sessionImplementor = mockk(relaxed = true)
        persistenceContext = mockk(relaxed = true)
        uowInterceptors = listOf(interceptor1, interceptor2)

        jpaUnitOfWork = TestableJpaUnitOfWork(
            uowInterceptors = uowInterceptors,
            persistListenerManager = persistListenerManager,
            supportEntityInlinePersistListener = true,
            supportValueObjectExistsCheckOnSave = true
        )

        // Set up entity manager
        jpaUnitOfWork.setTestEntityManager(entityManager)
        JpaUnitOfWork.fixAopWrapper(jpaUnitOfWork)

        // Reset ThreadLocal state
        JpaUnitOfWork.reset()

        // Mock entity manager delegate
        every { entityManager.delegate } returns sessionImplementor
        every { sessionImplementor.isClosed } returns false
        every { sessionImplementor.persistenceContext } returns persistenceContext
        every { persistenceContext.reentrantSafeEntityEntries() } returns emptyArray()

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
        every { entityManager.find(any<Class<*>>(), any()) } returns null
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
            sessionImplementor,
            persistenceContext,
            mockEntityInfo,
            answers = false,
            recordedCalls = true
        )
    }

    @Test
    @DisplayName("应该持久化非值对象实体")
    fun testPersistNormalEntity() {
        // Given
        val entity = TestEntity(1L, "test")

        // When
        jpaUnitOfWork.persist(entity)

        // Then
        jpaUnitOfWork.save()
        verify { entityManager.persist(entity) }
    }

    @Test
    @DisplayName("如果值对象已存在则不应持久化")
    fun testPersistValueObjectAlreadyExists() {
        // Given
        val valueObject = TestValueObject("test")
        every { entityManager.find(valueObject.javaClass, valueObject.hash()) } returns valueObject

        // When
        jpaUnitOfWork.persist(valueObject)

        // Then
        jpaUnitOfWork.save()
        verify(exactly = 0) { entityManager.persist(valueObject) }
    }

    @Test
    @DisplayName("如果值对象不存在则应持久化")
    fun testPersistValueObjectNotExists() {
        // Given
        val valueObject = TestValueObject("test")
        every { entityManager.find(valueObject.javaClass, valueObject.hash()) } returns null

        // When
        jpaUnitOfWork.persist(valueObject)

        // Then
        jpaUnitOfWork.save()
        verify { entityManager.persist(valueObject) }
    }

    @Test
    @DisplayName("持久化前应解包聚合对象")
    fun testPersistAggregateUnwrapping() {
        // Given
        val innerEntity = TestEntity(1L, "test")
        val aggregate = TestAggregate(innerEntity)

        // When
        jpaUnitOfWork.persist(aggregate)

        // Then
        jpaUnitOfWork.save()
        verify { entityManager.persist(innerEntity) }  // Should persist since contains() returns false by default
    }

    @Test
    @DisplayName("持久化不存在的实体时应返回true")
    fun testPersistIfNotExistWhenNotExists() {
        // Given
        val entity = TestEntity(null, "test")

        // When
        val result = jpaUnitOfWork.persistIfNotExist(entity)

        // Then
        assertTrue(result)
        jpaUnitOfWork.save()
        verify { entityManager.persist(entity) }
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
        jpaUnitOfWork.persist(entity)

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
        jpaUnitOfWork.persist(entity)

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
    @DisplayName("会话关闭时应返回空列表")
    fun testPersistenceContextEntitiesSessionClosed() {
        // Given
        every { sessionImplementor.isClosed } returns true

        // When
        val result = jpaUnitOfWork.testPersistenceContextEntities()

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    @DisplayName("发生异常时应返回空列表")
    fun testPersistenceContextEntitiesException() {
        // Given
        every { entityManager.delegate } throws RuntimeException("Test exception")

        // When
        val result = jpaUnitOfWork.testPersistenceContextEntities()

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    @DisplayName("应正确重置ThreadLocal变量")
    fun testReset() {
        // Given
        val entity = TestEntity(1L, "test")
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
            supportValueObjectExistsCheckOnSave = false
        )
        unitOfWork.setTestEntityManager(entityManager)
        JpaUnitOfWork.fixAopWrapper(unitOfWork)

        val entity = TestEntity(1L, "test")
        unitOfWork.persist(entity)

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
            supportValueObjectExistsCheckOnSave = false
        )

        // When
        JpaUnitOfWork.fixAopWrapper(newUnitOfWork)

        // Then
        assertSame(newUnitOfWork, JpaUnitOfWork.instance)
    }

    @Test
    @DisplayName("应处理带存在性检查的值对象持久化")
    fun testValueObjectPersistenceWithExistsCheck() {
        // Given
        val valueObject = TestValueObject("test")
        every { entityManager.find(valueObject.javaClass, valueObject.hash()) } returns null

        // When
        jpaUnitOfWork.persist(valueObject)
        jpaUnitOfWork.save()

        // Then
        verify { entityManager.persist(valueObject) }
    }

    @Test
    @Order(1)  // Run this test first to avoid interference from other tests
    @DisplayName("应处理聚合对象的包装和解包")
    fun testAggregateWrappingUnwrapping() {
        // Extra reset to ensure clean state for this specific test
        JpaUnitOfWork.reset()

        // Given
        val originalEntity = TestEntity(1L, "original")
        val aggregate = TestAggregate(originalEntity)
        val mergedEntity = TestEntity(1L, "merged")

        // Mock the entity as NOT new, so it should use merge path
        every { mockEntityInfo.isNew(originalEntity) } returns false
        every { entityManager.merge(originalEntity) } returns mergedEntity
        // Ensure contains returns false so merge path is taken
        every { entityManager.contains(originalEntity) } returns false

        // When
        jpaUnitOfWork.persist(aggregate)
        jpaUnitOfWork.save()

        // Then
        verify { entityManager.merge(originalEntity) }
        // Verify the aggregate was updated with the merged entity
        assertEquals(mergedEntity, aggregate._unwrap())
    }

    // Test helper classes
    @jakarta.persistence.Entity
    data class TestEntity(
        @jakarta.persistence.Id
        val id: Long?,
        val name: String
    )

    @jakarta.persistence.Entity
    data class TestValueObject(
        @jakarta.persistence.Id
        private val value: String
    ) : ValueObject<String> {
        override fun hash(): String = value.hashCode().toString()
    }

    class TestAggregate(private var entity: TestEntity) : Aggregate<TestEntity> {
        override fun _unwrap(): TestEntity = entity
        override fun _wrap(entity: TestEntity) {
            this.entity = entity
        }
    }
}
