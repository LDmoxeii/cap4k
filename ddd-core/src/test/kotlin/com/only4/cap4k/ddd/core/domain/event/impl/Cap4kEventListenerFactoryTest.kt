package com.only4.cap4k.ddd.core.domain.event.impl

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.event.EventListener
import org.springframework.transaction.event.TransactionalEventListener

@DisplayName("Cap4kEventListenerFactory 测试")
class Cap4kEventListenerFactoryTest {

    @AfterEach
    fun tearDown() {
        EventRuntimeContext.reset()
    }

    @Test
    @DisplayName("工厂应该支持普通 EventListener 且不接管 TransactionalEventListener")
    fun `factory supports regular event listeners but not transactional event listeners`() {
        val factory = Cap4kEventListenerFactory()

        assertTrue(factory.supportsMethod(listenerMethod("regularListener")))
        assertFalse(factory.supportsMethod(listenerMethod("transactionalListener")))
    }

    @Test
    @DisplayName("监听器失败时应该抛出包含监听器和事件诊断信息的异常")
    fun `failing listener invocation throws diagnostic exception`() {
        val scope = EventRuntimeContext.push(EventRuntimeScopeType.DOMAIN_DISPATCH)
        val adapter = TestableCap4kApplicationListenerMethodAdapter(
            beanName = "diagnosticListener",
            targetClass = ListenerMethods::class.java,
            method = listenerMethod("failingListener"),
            targetBean = ListenerMethods()
        )

        val exception = assertThrows<EventListenerInvocationException> {
            adapter.invoke(TestPayload("boom"))
        }

        assertEquals("diagnosticListener", exception.listenerBeanName)
        assertEquals(ListenerMethods::class.java, exception.listenerClass)
        assertEquals("failingListener", exception.listenerMethod.name)
        assertEquals(TestPayload::class.java, exception.eventPayloadClass)
        assertSame(ListenerMethods.failure, exception.cause)
        assertEquals(EventRuntimeScopeType.DOMAIN_DISPATCH.name, exception.diagnosticContext?.scopeType)
        assertEquals("diagnosticListener", exception.diagnosticContext?.listenerBeanName)
        assertEquals(ListenerMethods::class.java.name, exception.diagnosticContext?.listenerClassName)
        assertEquals("failingListener", exception.diagnosticContext?.listenerMethodName)

        EventRuntimeContext.pop(scope)
    }

    @Test
    @DisplayName("监听器元数据成功后应该恢复")
    fun `listener metadata is restored after successful invocation`() {
        val scope = EventRuntimeContext.push(EventRuntimeScopeType.REQUEST)
        scope.listenerBeanName = "previousBean"
        scope.listenerClass = ExistingListener::class.java
        scope.listenerMethod = ExistingListener::class.java.getDeclaredMethod("previous")

        val targetBean = ListenerMethods()
        val adapter = TestableCap4kApplicationListenerMethodAdapter(
            beanName = "successListener",
            targetClass = ListenerMethods::class.java,
            method = listenerMethod("successfulListener"),
            targetBean = targetBean
        )

        adapter.invoke(TestPayload("ok"))

        assertEquals(
            ListenerInvocation(
                beanName = "successListener",
                listenerClass = ListenerMethods::class.java,
                methodName = "successfulListener",
            ),
            targetBean.invocations.single()
        )
        assertEquals("previousBean", scope.listenerBeanName)
        assertEquals(ExistingListener::class.java, scope.listenerClass)
        assertEquals("previous", scope.listenerMethod?.name)

        EventRuntimeContext.pop(scope)
    }

    @Test
    @DisplayName("监听器元数据失败后也应该恢复")
    fun `listener metadata is restored after failed invocation`() {
        val scope = EventRuntimeContext.push(EventRuntimeScopeType.REQUEST)

        val adapter = TestableCap4kApplicationListenerMethodAdapter(
            beanName = "failureListener",
            targetClass = ListenerMethods::class.java,
            method = listenerMethod("failingListener"),
            targetBean = ListenerMethods()
        )

        assertThrows<EventListenerInvocationException> {
            adapter.invoke(TestPayload("boom"))
        }

        assertNull(scope.listenerBeanName)
        assertNull(scope.listenerClass)
        assertNull(scope.listenerMethod)

        EventRuntimeContext.pop(scope)
    }

    @Test
    @DisplayName("监听器返回值发布事件应该被拒绝")
    fun `listener return value event publication is rejected`() {
        val scope = EventRuntimeContext.push(EventRuntimeScopeType.REQUEST)
        val adapter = TestableCap4kApplicationListenerMethodAdapter(
            beanName = "returningListener",
            targetClass = ListenerMethods::class.java,
            method = listenerMethod("returningListener"),
            targetBean = ListenerMethods()
        )

        val exception = assertThrows<EventListenerInvocationException> {
            adapter.invoke(TestPayload("return"))
        }

        assertEquals("returningListener", exception.listenerBeanName)
        assertEquals("returningListener", exception.diagnosticContext?.listenerBeanName)
        assertTrue(exception.cause is UnsupportedOperationException)
        assertTrue(exception.message?.contains("returningListener") == true)
        assertNull(scope.listenerBeanName)
        assertNull(scope.listenerClass)
        assertNull(scope.listenerMethod)

        EventRuntimeContext.pop(scope)
    }

    private class TestableCap4kApplicationListenerMethodAdapter(
        beanName: String,
        targetClass: Class<*>,
        method: java.lang.reflect.Method,
        private val targetBean: Any,
    ) : Cap4kApplicationListenerMethodAdapter(beanName, targetClass, method) {

        fun invoke(vararg args: Any?): Any? = doInvoke(*args)

        override fun getTargetBean(): Any = targetBean
    }

    private class ListenerMethods {
        val invocations: MutableList<ListenerInvocation> = mutableListOf()

        @EventListener
        fun regularListener(event: TestPayload) {
        }

        @TransactionalEventListener
        fun transactionalListener(event: TestPayload) {
        }

        @EventListener
        fun successfulListener(event: TestPayload) {
            val scope = EventRuntimeContext.current()
            invocations += ListenerInvocation(
                beanName = scope.listenerBeanName,
                listenerClass = scope.listenerClass,
                methodName = scope.listenerMethod?.name,
            )
        }

        @EventListener
        fun failingListener(event: TestPayload) {
            throw failure
        }

        @EventListener
        fun returningListener(event: TestPayload): TestPayload = event

        companion object {
            val failure = IllegalStateException("listener failed")
        }
    }

    private class ExistingListener {
        fun previous() {
        }
    }

    private data class ListenerInvocation(
        val beanName: String?,
        val listenerClass: Class<*>?,
        val methodName: String?,
    )

    private data class TestPayload(val value: String)

    private fun listenerMethod(name: String): java.lang.reflect.Method =
        ListenerMethods::class.java.getDeclaredMethod(name, TestPayload::class.java)
}
