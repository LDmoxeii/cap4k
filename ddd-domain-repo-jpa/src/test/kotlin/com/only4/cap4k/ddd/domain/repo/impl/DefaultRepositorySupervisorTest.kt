package com.only4.cap4k.ddd.domain.repo.impl

import com.only4.cap4k.ddd.application.JpaRepositoryObservationRecorder
import com.only4.cap4k.ddd.core.application.AggregatePersistenceIntentRecorder
import com.only4.cap4k.ddd.core.application.invocation.DefaultInvocationScopeManager
import com.only4.cap4k.ddd.core.application.invocation.InvocationKind
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateRootCatalog
import com.only4.cap4k.ddd.core.domain.repo.Predicate
import com.only4.cap4k.ddd.core.domain.repo.Repository
import com.only4.cap4k.ddd.core.share.DomainException
import com.only4.cap4k.ddd.core.share.OrderInfo
import com.only4.cap4k.ddd.core.share.PageData
import com.only4.cap4k.ddd.core.share.PageParam
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DefaultRepositorySupervisorTest {
    private val persistenceIntents = mockk<AggregatePersistenceIntentRecorder>(relaxed = true)
    private val observationRecorder = mockk<JpaRepositoryObservationRecorder>(relaxed = true)
    private val repository = mockk<Repository<TestEntity>>(relaxed = true)
    private val childRepository = mockk<Repository<AnotherEntity>>(relaxed = true)
    private val scopes = DefaultInvocationScopeManager()
    private val aggregateRoots = AggregateRootCatalog { entityType -> entityType == TestEntity::class.java }
    private lateinit var supervisor: DefaultRepositorySupervisor

    private data class TestEntity(val id: Long)
    private data class AnotherEntity(val id: String)
    private data class MissingEntity(val id: String)
    private class TestPredicate : Predicate<TestEntity>
    private class UnsupportedPredicate : Predicate<TestEntity>
    private class AnotherPredicate : Predicate<AnotherEntity>
    private class MissingPredicate : Predicate<MissingEntity>

    @BeforeEach
    fun setup() {
        every { repository.supportPredicateClass() } returns TestPredicate::class.java
        every { childRepository.supportPredicateClass() } returns AnotherPredicate::class.java
        DefaultRepositorySupervisor.registerPredicateEntityClassReflector(TestPredicate::class.java) {
            TestEntity::class.java
        }
        DefaultRepositorySupervisor.registerPredicateEntityClassReflector(AnotherPredicate::class.java) {
            AnotherEntity::class.java
        }
        DefaultRepositorySupervisor.registerPredicateEntityClassReflector(MissingPredicate::class.java) {
            MissingEntity::class.java
        }
        mockkStatic("com.only4.cap4k.ddd.core.share.misc.ClassUtils")
        every {
            com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass(repository, 0, Repository::class.java)
        } returns TestEntity::class.java
        every {
            com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass(childRepository, 0, Repository::class.java)
        } returns AnotherEntity::class.java
        supervisor = DefaultRepositorySupervisor(
            repositories = listOf(repository, childRepository),
            persistenceIntents = persistenceIntents,
            invocationScopeAccessor = scopes,
            aggregateRootCatalog = aggregateRoots,
            observationRecorder = observationRecorder,
        ).apply { init() }
    }

    @AfterEach
    fun teardown() {
        unmockkStatic("com.only4.cap4k.ddd.core.share.misc.ClassUtils")
    }

    @Test
    fun `command read stays managed and records observation without existing intent`() {
        val predicate = TestPredicate()
        val entities = listOf(TestEntity(1), TestEntity(2))
        every { repository.find(predicate, any<Collection<OrderInfo>>()) } returns entities

        val result = inScope(InvocationKind.COMMAND) { supervisor.find(predicate) }

        assertEquals(entities, result)
        entities.forEach { verify { observationRecorder.observeRepositoryLoad(it) } }
        verify(exactly = 0) { persistenceIntents.registerNew(any()) }
        verify(exactly = 0) { persistenceIntents.registerDelete(any()) }
    }

    @Test
    fun `query read is allowed and records observation`() {
        val predicate = TestPredicate()
        val entity = TestEntity(1)
        every { repository.findOne(predicate) } returns entity

        assertEquals(entity, inScope(InvocationKind.QUERY) { supervisor.findOne(predicate) })
        verify { observationRecorder.observeRepositoryLoad(entity) }
    }

    @Test
    fun `query may read an owned child repository`() {
        val predicate = AnotherPredicate()
        val child = AnotherEntity("child-1")
        every { childRepository.findOne(predicate) } returns child

        assertEquals(child, inScope(InvocationKind.QUERY) { supervisor.findOne(predicate) })
    }

    @Test
    fun `command first read and removal reject an owned child repository`() {
        val predicate = AnotherPredicate()

        assertThrows(IllegalStateException::class.java) {
            inScope(InvocationKind.COMMAND) { supervisor.findOne(predicate) }
        }
        assertThrows(IllegalStateException::class.java) {
            inScope(InvocationKind.COMMAND) { supervisor.remove(predicate) }
        }
        verify(exactly = 0) { childRepository.findOne(any()) }
        verify(exactly = 0) { childRepository.find(any(), any<Collection<OrderInfo>>()) }
        verify(exactly = 0) { persistenceIntents.registerDelete(any()) }
    }

    @Test
    fun `command removal loads roots and records delete intent`() {
        val predicate = TestPredicate()
        val entities = listOf(TestEntity(1), TestEntity(2))
        every { repository.find(predicate, any<Collection<OrderInfo>>()) } returns entities

        val result = inScope(InvocationKind.COMMAND) { supervisor.remove(predicate) }

        assertEquals(entities, result)
        entities.forEach { verify { persistenceIntents.registerDelete(it) } }
    }

    @Test
    fun `capability and ordinary scope cannot read repository`() {
        assertThrows(IllegalStateException::class.java) { supervisor.count(TestPredicate()) }
        assertThrows(IllegalStateException::class.java) {
            inScope(InvocationKind.CAPABILITY) { supervisor.count(TestPredicate()) }
        }
    }

    @Test
    fun `query cannot remove aggregate`() {
        assertThrows(IllegalStateException::class.java) {
            inScope(InvocationKind.QUERY) { supervisor.remove(TestPredicate()) }
        }
    }

    @Test
    fun `unsupported predicate and missing repository retain stable domain failures`() {
        assertThrows(DomainException::class.java) {
            inScope(InvocationKind.COMMAND) { supervisor.find(UnsupportedPredicate()) }
        }
        assertThrows(DomainException::class.java) {
            inScope(InvocationKind.QUERY) { supervisor.find(MissingPredicate()) }
        }
    }

    @Test
    fun `repository reflector supports erased repository subclasses`() {
        class ErasedRepository : Repository<TestEntity> {
            override fun supportPredicateClass(): Class<*> = TestPredicate::class.java
            override fun find(predicate: Predicate<TestEntity>, orders: Collection<OrderInfo>) = emptyList<TestEntity>()
            override fun find(predicate: Predicate<TestEntity>, pageParam: PageParam) = emptyList<TestEntity>()
            override fun findOne(predicate: Predicate<TestEntity>): TestEntity? = null
            override fun findFirst(predicate: Predicate<TestEntity>, orders: Collection<OrderInfo>): TestEntity? = null
            override fun findPage(predicate: Predicate<TestEntity>, pageParam: PageParam) =
                PageData.create(pageParam, 0, emptyList<TestEntity>())
            override fun count(predicate: Predicate<TestEntity>) = 0L
            override fun exists(predicate: Predicate<TestEntity>) = false
        }

        DefaultRepositorySupervisor.registerRepositoryEntityClassReflector(ErasedRepository::class.java) {
            TestEntity::class.java
        }

        val field = DefaultRepositorySupervisor::class.java
            .getDeclaredField("repositoryClass2EntityClassReflector")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val reflectors = field.get(null) as Map<Class<*>, *>
        assertEquals(true, reflectors.containsKey(ErasedRepository::class.java))
    }

    private fun <T> inScope(kind: InvocationKind, block: () -> T): T {
        val scope = scopes.enter(kind)
        return try {
            block()
        } finally {
            scope.close()
        }
    }
}
