package com.only4.cap4k.ddd.core.domain.event.impl

import com.only4.cap4k.ddd.core.domain.event.EventRuntimeContextManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

@DisplayName("EventRuntimeContext 测试")
class EventRuntimeContextTest {

    @AfterEach
    fun tearDown() {
        EventRuntimeContext.reset()
    }

    @Nested
    @DisplayName("作用域栈测试")
    inner class ScopeStackTests {

        @Test
        @DisplayName("嵌套作用域应该隔离集成事件附件")
        fun `nested scopes isolate integration attachments`() {
            val outer = EventRuntimeContext.push(EventRuntimeScopeType.APPLICATION_INVOCATION)
            outer.attachIntegration(EventAttachment.eager(TestIntegrationEvent("outer")))

            val inner = EventRuntimeContext.push(EventRuntimeScopeType.DOMAIN_DISPATCH)
            inner.attachIntegration(EventAttachment.eager(TestIntegrationEvent("inner")))

            assertEquals(listOf(TestIntegrationEvent("inner")), inner.integrationAttachments.map { it.resolve() })
            assertEquals(listOf(TestIntegrationEvent("outer")), outer.integrationAttachments.map { it.resolve() })
        }

        @Test
        @DisplayName("弹出内层作用域后应该恢复外层作用域")
        fun `popping inner scope restores outer scope`() {
            val outer = EventRuntimeContext.push(EventRuntimeScopeType.APPLICATION_INVOCATION)
            val inner = EventRuntimeContext.push(EventRuntimeScopeType.DOMAIN_DISPATCH)

            assertSame(inner, EventRuntimeContext.current())

            EventRuntimeContext.pop(inner)

            assertSame(outer, EventRuntimeContext.current())
            assertTrue(EventRuntimeContext.hasScope())
        }

        @Test
        @DisplayName("丢弃内层作用域不应该丢弃外层附件")
        fun `discarding inner scope does not discard outer attachments`() {
            val outer = EventRuntimeContext.push(EventRuntimeScopeType.APPLICATION_INVOCATION)
            outer.attachIntegration(EventAttachment.eager(TestIntegrationEvent("outer")))

            val inner = EventRuntimeContext.push(EventRuntimeScopeType.DOMAIN_DISPATCH)
            inner.attachIntegration(EventAttachment.eager(TestIntegrationEvent("inner")))

            EventRuntimeContext.discard(inner)
            EventRuntimeContext.pop(inner)

            assertTrue(inner.integrationAttachments.isEmpty())
            assertEquals(listOf(TestIntegrationEvent("outer")), EventRuntimeContext.current().integrationAttachments.map { it.resolve() })
        }

        @Test
        @DisplayName("重置应该清理所有作用域")
        fun `reset clears every scope`() {
            val outer = EventRuntimeContext.push(EventRuntimeScopeType.APPLICATION_INVOCATION)
            outer.attachIntegration(EventAttachment.eager(TestIntegrationEvent("outer")))
            outer.attachDomain(EqualEntity("outer"), EventAttachment.eager(TestDomainEvent("outer")))
            val inner = EventRuntimeContext.push(EventRuntimeScopeType.DOMAIN_DISPATCH)
            inner.attachIntegration(EventAttachment.eager(TestIntegrationEvent("inner")))
            inner.attachDomain(EqualEntity("inner"), EventAttachment.eager(TestDomainEvent("inner")))

            EventRuntimeContextManager.reset()

            assertTrue(outer.integrationAttachments.isEmpty())
            assertTrue(outer.domainAttachments.isEmpty())
            assertTrue(inner.integrationAttachments.isEmpty())
            assertTrue(inner.domainAttachments.isEmpty())
            assertFalse(EventRuntimeContext.hasScope())
            assertNull(EventRuntimeContext.currentOrNull())
        }

        @Test
        @DisplayName("相等的数据类集成事件应该保留为两个附件")
        fun `equal data class integration payloads attached twice stay as two entries`() {
            val scope = EventRuntimeContext.push(EventRuntimeScopeType.APPLICATION_INVOCATION)
            val payload = TestIntegrationEvent("same")

            scope.attachIntegration(EventAttachment.eager(payload))
            scope.attachIntegration(EventAttachment.eager(payload.copy()))

            assertEquals(2, scope.integrationAttachments.size)
            assertEquals(
                listOf(TestIntegrationEvent("same"), TestIntegrationEvent("same")),
                scope.integrationAttachments.map { it.resolve() }
            )
        }
    }

    @Nested
    @DisplayName("惰性附件测试")
    inner class LazyAttachmentTests {

        @Test
        @DisplayName("供应者对象本身不应该作为事件载荷")
        fun `supplier object itself is not treated as payload`() {
            var evaluated = false
            val supplier = {
                evaluated = true
                TestIntegrationEvent("resolved")
            }
            val attachment = EventAttachment.lazy(supplier = supplier)

            assertFalse(attachment.matches(supplier))
            assertFalse(evaluated)
        }

        @Test
        @DisplayName("供应者应该只在解析时求值")
        fun `supplier is evaluated only on resolve`() {
            var evaluations = 0
            val attachment = EventAttachment.lazy {
                evaluations += 1
                TestIntegrationEvent("resolved")
            }

            assertEquals(0, evaluations)

            assertEquals(TestIntegrationEvent("resolved"), attachment.resolve())

            assertEquals(1, evaluations)
        }

        @Test
        @DisplayName("丢弃作用域不应该求值供应者")
        fun `supplier is not evaluated when discarded`() {
            var evaluations = 0
            val scope = EventRuntimeContext.push(EventRuntimeScopeType.DOMAIN_DISPATCH)
            scope.attachIntegration(EventAttachment.lazy {
                evaluations += 1
                TestIntegrationEvent("discarded")
            })

            EventRuntimeContext.discard(scope)

            assertEquals(0, evaluations)
            assertTrue(scope.integrationAttachments.isEmpty())
        }

        @Test
        @DisplayName("重置包含惰性附件的作用域不应该求值供应者")
        fun `reset clears scope with lazy attachment without resolving supplier`() {
            var evaluations = 0
            val scope = EventRuntimeContext.push(EventRuntimeScopeType.APPLICATION_INVOCATION)
            scope.attachIntegration(EventAttachment.lazy {
                evaluations += 1
                TestIntegrationEvent("reset")
            })

            EventRuntimeContext.reset()

            assertEquals(0, evaluations)
            assertTrue(scope.integrationAttachments.isEmpty())
            assertFalse(EventRuntimeContext.hasScope())
        }

        @Test
        @DisplayName("附件应该保存独立的调度时间")
        fun `schedule is stored per attachment`() {
            val firstSchedule = LocalDateTime.of(2026, 5, 21, 10, 0)
            val secondSchedule = LocalDateTime.of(2026, 5, 21, 11, 0)

            val first = EventAttachment.eager(TestIntegrationEvent("first"), firstSchedule)
            val second = EventAttachment.lazy(secondSchedule) { TestIntegrationEvent("second") }

            assertEquals(firstSchedule, first.schedule)
            assertEquals(secondSchedule, second.schedule)
        }
    }

    @Nested
    @DisplayName("领域事件附件测试")
    inner class DomainAttachmentTests {

        @Test
        @DisplayName("领域事件附件应该绑定实体且不丢失")
        fun `domain attachments stay entity bound without defining sibling order`() {
            val scope = EventRuntimeContext.push(EventRuntimeScopeType.APPLICATION_INVOCATION)
            val firstEntity = EqualEntity("same")
            val secondEntity = EqualEntity("same")

            scope.attachDomain(firstEntity, EventAttachment.eager(TestDomainEvent("first")))
            scope.attachDomain(firstEntity, EventAttachment.eager(TestDomainEvent("second")))
            scope.attachDomain(secondEntity, EventAttachment.eager(TestDomainEvent("third")))

            assertEquals(
                setOf(TestDomainEvent("first"), TestDomainEvent("second")),
                scope.domainAttachments[firstEntity]?.map { it.resolve() }?.toSet()
            )
            assertEquals(setOf(TestDomainEvent("third")), scope.domainAttachments[secondEntity]?.map { it.resolve() }?.toSet())
            assertEquals(2, scope.domainAttachments.size)
        }
    }

    @Test
    @DisplayName("没有作用域时应该创建环境兼容作用域")
    fun `currentOrCreateAmbient creates ambient scope when no scope exists`() {
        val scope = EventRuntimeContext.currentOrCreateAmbient()

        assertEquals(EventRuntimeScopeType.AMBIENT, scope.type)
        assertSame(scope, EventRuntimeContext.current())
    }

    @Test
    @DisplayName("Caller Runs 异步任务应该隔离并恢复调用方事件状态")
    fun `isolated state hides and restores caller event runtime`() {
        val outer = EventRuntimeContext.push(EventRuntimeScopeType.APPLICATION_INVOCATION)
        outer.attachIntegration(EventAttachment.eager(TestIntegrationEvent("outer")))

        EventRuntimeContext.withCausalFrame("Command:Outer") {
            EventRuntimeContext.withIsolatedState {
                assertFalse(EventRuntimeContext.hasScope())
                assertEquals(emptyList<String>(), EventRuntimeContext.diagnosticCausalPath())
                EventRuntimeContext.currentOrCreateAmbient()
                    .attachIntegration(EventAttachment.eager(TestIntegrationEvent("isolated")))
            }

            assertSame(outer, EventRuntimeContext.current())
            assertEquals(listOf("Command:Outer"), EventRuntimeContext.diagnosticCausalPath())
            assertEquals(
                listOf(TestIntegrationEvent("outer")),
                outer.integrationAttachments.map { it.resolve() },
            )
        }
    }

    @Test
    @DisplayName("诊断因果路径应该保留最深失败链并在下一次成功调用时清理")
    fun `diagnostic causal path retains the deepest failing frame and resets for the next call`() {
        runCatching {
            EventRuntimeContext.withCausalFrame("Command:CreateOrder") {
                EventRuntimeContext.withCausalFrame("Event:OrderCreated") {
                    EventRuntimeContext.withCausalFrame("Handler:ReserveInventory") {
                        error("retry loop")
                    }
                }
            }
        }

        assertEquals(
            listOf("Command:CreateOrder", "Event:OrderCreated", "Handler:ReserveInventory"),
            EventRuntimeContextManager.diagnosticCausalPath(),
        )

        EventRuntimeContext.withCausalFrame("Query:CurrentOrder") {
            assertEquals(
                listOf("Query:CurrentOrder"),
                EventRuntimeContextManager.diagnosticCausalPath(),
            )
        }

        assertEquals(emptyList<String>(), EventRuntimeContextManager.diagnosticCausalPath())
    }

    @Test
    @DisplayName("诊断因果路径应该在外层调用活跃期间保留已完成的最深链")
    fun `diagnostic causal path retains the deepest completed chain while the outer call is active`() {
        EventRuntimeContext.withCausalFrame("Command:CreateOrder") {
            EventRuntimeContext.withCausalFrame("Event:OrderCreated") {
                EventRuntimeContext.withCausalFrame("Handler:ReserveInventory") {
                    assertEquals(
                        listOf("Command:CreateOrder", "Event:OrderCreated", "Handler:ReserveInventory"),
                        EventRuntimeContextManager.diagnosticCausalPath(),
                    )
                }
            }

            assertEquals(
                listOf("Command:CreateOrder", "Event:OrderCreated", "Handler:ReserveInventory"),
                EventRuntimeContextManager.diagnosticCausalPath(),
            )
        }

        assertEquals(emptyList<String>(), EventRuntimeContextManager.diagnosticCausalPath())
    }

    data class TestIntegrationEvent(val message: String)
    data class TestDomainEvent(val message: String)
    data class EqualEntity(val id: String)
}
