package com.only4.cap4k.ddd.core.domain.aggregate.impl

import com.only4.cap4k.ddd.core.application.AggregatePersistenceIntentRecorder
import com.only4.cap4k.ddd.core.application.invocation.InvocationKind
import com.only4.cap4k.ddd.core.application.invocation.InvocationScopeAccessor
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateLifecycleInvoker
import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload
import com.only4.cap4k.ddd.core.application.context.ExecutionContextSnapshot
import com.only4.cap4k.ddd.core.domain.managed.ManagedEntityAdmissionCoordinator
import com.only4.cap4k.ddd.core.domain.managed.ManagedEntityAdmissionKind
import com.only4.cap4k.ddd.core.share.DomainException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("DefaultAggregateFactorySupervisor tests")
class DefaultAggregateFactorySupervisorTest {
    private lateinit var persistenceIntents: AggregatePersistenceIntentRecorder
    private val commandScope = InvocationScopeAccessor { InvocationKind.COMMAND }

    @BeforeEach
    fun setup() {
        persistenceIntents = mockk(relaxed = true)
    }

    @Test
    fun `matching factory creates aggregate and registers root create intent`() {
        val supervisor = supervisor(TestAggregateFactory())

        val result = supervisor.create(TestPayload("test-data"))

        assertNotNull(result)
        assertEquals("test-data", result.data)
        verify { persistenceIntents.registerNew(result) }
    }

    @Test
    fun `root admission makes aggregate ids ready before persistence intent registration`() {
        var onCreateObserved = false
        val admission = object : ManagedEntityAdmissionCoordinator {
            override fun admit(entity: Any, kind: ManagedEntityAdmissionKind) {
                assertEquals(ManagedEntityAdmissionKind.AGGREGATE_ROOT, kind)
                (entity as ReadyRoot).also { root ->
                    root.id = "ROOT-1"
                    root.children.forEachIndexed { index, child -> child.id = "CHILD-${index + 1}" }
                }
            }

            override fun validate(entity: Any, executionContext: ExecutionContextSnapshot) = Unit
        }
        val lifecycleInvoker = object : AggregateLifecycleInvoker {
            override fun onCreate(root: Any) {
                assertEquals("ROOT-1", (root as ReadyRoot).id)
                assertEquals(listOf("CHILD-1", "CHILD-2"), root.children.map { it.id })
                onCreateObserved = true
            }

            override fun onDeleted(root: Any) = Unit
        }
        every { persistenceIntents.registerNew(any()) } answers {
            assertEquals("ROOT-1", firstArg<ReadyRoot>().id)
        }
        val supervisor = DefaultAggregateFactorySupervisor(
            factories = listOf(ReadyAggregateFactory()),
            persistenceIntents = persistenceIntents,
            invocationScopeAccessor = commandScope,
            lifecycleInvoker = lifecycleInvoker,
            managedEntityAdmissionCoordinator = admission,
        )

        val result = supervisor.create(ReadyPayload(2))

        assertEquals("ROOT-1", result.id)
        assertEquals(listOf("CHILD-1", "CHILD-2"), result.children.map { it.id })
        assertTrue(onCreateObserved)
        verify(exactly = 1) { persistenceIntents.registerNew(result) }
    }

    @Test
    fun `missing factory fails without registering persistence intent`() {
        val supervisor = supervisor()

        val exception = assertThrows(DomainException::class.java) {
            supervisor.create(TestPayload("test-data"))
        }

        assertTrue(exception.message.orEmpty().contains("No factory found for payload"))
        verify(exactly = 0) { persistenceIntents.registerNew(any()) }
    }

    @Test
    fun `different payload types resolve their matching factories`() {
        val supervisor = supervisor(TestAggregateFactory(), AnotherAggregateFactory())

        val first = supervisor.create(TestPayload("test-data"))
        val second = supervisor.create(AnotherPayload("another-data"))

        assertEquals("test-data", first.data)
        assertEquals("another-data", second.data)
        verify { persistenceIntents.registerNew(first) }
        verify { persistenceIntents.registerNew(second) }
    }

    @Test
    fun `persistence intent failure is propagated`() {
        every { persistenceIntents.registerNew(any()) } throws RuntimeException("Database error")
        val supervisor = supervisor(TestAggregateFactory())

        val exception = assertThrows(RuntimeException::class.java) {
            supervisor.create(TestPayload("test-data"))
        }

        assertEquals("Database error", exception.message)
    }

    @Test
    fun `factory access outside Command scope fails before creation`() {
        val supervisor = DefaultAggregateFactorySupervisor(
            listOf(TestAggregateFactory()),
            persistenceIntents,
            InvocationScopeAccessor { InvocationKind.QUERY },
        )

        val exception = assertThrows(IllegalStateException::class.java) {
            supervisor.create(TestPayload("test-data"))
        }

        assertTrue(exception.message.orEmpty().contains("COMMAND"))
        verify(exactly = 0) { persistenceIntents.registerNew(any()) }
    }

    @Test
    fun `concurrent create requests use the same immutable factory catalog`() {
        val supervisor = supervisor(TestAggregateFactory())
        val payload = TestPayload("concurrent-data")

        val threads = List(10) { Thread { supervisor.create(payload) } }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)

        verify(exactly = 10) { persistenceIntents.registerNew(any()) }
    }

    private fun supervisor(vararg factories: AggregateFactory<*, *>): DefaultAggregateFactorySupervisor =
        DefaultAggregateFactorySupervisor(
            factories.toList(),
            persistenceIntents,
            commandScope,
        )

    data class TestPayload(val data: String) : AggregatePayload<TestEntity>
    data class AnotherPayload(val data: String) : AggregatePayload<AnotherEntity>
    data class TestEntity(val data: String)
    data class AnotherEntity(val data: String)

    class TestAggregateFactory : AggregateFactory<TestPayload, TestEntity> {
        override fun create(entityPayload: TestPayload): TestEntity = TestEntity(entityPayload.data)
    }

    class AnotherAggregateFactory : AggregateFactory<AnotherPayload, AnotherEntity> {
        override fun create(entityPayload: AnotherPayload): AnotherEntity = AnotherEntity(entityPayload.data)
    }

    private data class ReadyChild(var id: String? = null)
    private data class ReadyRoot(
        var id: String? = null,
        val children: MutableList<ReadyChild>,
    )

    private data class ReadyPayload(val childCount: Int) : AggregatePayload<ReadyRoot>

    private class ReadyAggregateFactory : AggregateFactory<ReadyPayload, ReadyRoot> {
        override fun create(entityPayload: ReadyPayload): ReadyRoot =
            ReadyRoot(children = MutableList(entityPayload.childCount) { ReadyChild() })
    }
}
