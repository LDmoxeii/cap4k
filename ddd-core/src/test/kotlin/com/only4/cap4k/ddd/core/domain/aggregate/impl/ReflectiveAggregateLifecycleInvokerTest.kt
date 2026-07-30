package com.only4.cap4k.ddd.core.domain.aggregate.impl

import com.only4.cap4k.ddd.core.domain.repo.impl.lifecycle.TestEntityWithBehaviorHooks
import com.only4.cap4k.ddd.core.domain.repo.impl.lifecycle.TestEntityWithBehaviorHooksProxy
import com.only4.cap4k.ddd.core.domain.repo.impl.lifecycle.TestEntityWithInitializingBehaviorFile
import com.only4.cap4k.ddd.core.domain.repo.impl.lifecycle.TestEntityWithMemberAndBehaviorHooks
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ReflectiveAggregateLifecycleInvokerTest {
    private val invoker = ReflectiveAggregateLifecycleInvoker()

    @Test
    fun `optional callbacks are invoked when present`() {
        val root = CallbackRoot()

        invoker.onCreate(root)
        invoker.onDeleted(root)

        assertEquals(listOf("create", "deleted"), root.calls)
    }

    @Test
    fun `missing callbacks are ignored`() {
        invoker.onCreate(Any())
        invoker.onDeleted(Any())
    }

    @Test
    fun `checked in behavior extensions remain optional lifecycle callbacks`() {
        val root = TestEntityWithBehaviorHooks()

        invoker.onCreate(root)
        invoker.onDeleted(root)

        assertEquals(1, root.onCreateCallCount)
        assertEquals(1, root.onDeletedCallCount)
    }

    @Test
    fun `behavior extension declared for aggregate type accepts provider proxy subtype`() {
        val root = TestEntityWithBehaviorHooksProxy()

        invoker.onCreate(root)
        invoker.onDeleted(root)

        assertEquals(1, root.onCreateCallCount)
        assertEquals(1, root.onDeletedCallCount)
    }

    @Test
    fun `member callback takes precedence over behavior extension`() {
        val root = TestEntityWithMemberAndBehaviorHooks()

        invoker.onCreate(root)

        assertEquals(1, root.memberCreateCallCount)
        assertEquals(0, root.behaviorCreateCallCount)
    }

    @Test
    fun `missing callback lookup does not initialize checked in behavior file`() {
        invoker.onCreate(TestEntityWithInitializingBehaviorFile())
    }

    @Test
    fun `callback failure is exposed without reflection wrapper`() {
        val error = assertThrows<LifecycleFailure> {
            invoker.onCreate(FailingCallbackRoot())
        }

        assertEquals("create failed", error.message)
    }

    class CallbackRoot {
        val calls = mutableListOf<String>()

        fun onCreate() {
            calls += "create"
        }

        fun onDeleted() {
            calls += "deleted"
        }
    }

    class FailingCallbackRoot {
        fun onCreate(): Unit = throw LifecycleFailure("create failed")
    }

    class LifecycleFailure(message: String) : RuntimeException(message)
}
