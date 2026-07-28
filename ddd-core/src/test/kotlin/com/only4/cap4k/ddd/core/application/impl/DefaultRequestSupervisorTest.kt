package com.only4.cap4k.ddd.core.application.impl

import com.only4.cap4k.ddd.core.CapabilityUnavailableException
import com.only4.cap4k.ddd.core.application.RequestHandler
import com.only4.cap4k.ddd.core.application.RequestParam
import jakarta.validation.Validator
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DefaultRequestSupervisorTest {
    data class TestRequest(val value: String) : RequestParam<String>

    @Test
    fun `send dispatches in current thread without a request repository`() {
        val handler = object : RequestHandler<TestRequest, String> {
            override fun exec(request: TestRequest): String = "handled:${request.value}"
        }
        val supervisor = DefaultRequestSupervisor(listOf(handler), emptyList(), null).apply { init() }

        assertEquals("handled:ok", supervisor.send(TestRequest("ok")))
    }

    @Test
    fun `handler exception keeps its original identity`() {
        val failure = IllegalStateException("business failure")
        val handler = object : RequestHandler<TestRequest, String> {
            override fun exec(request: TestRequest): String = throw failure
        }
        val supervisor = DefaultRequestSupervisor(listOf(handler), emptyList(), null)

        assertSame(failure, assertThrows<IllegalStateException> { supervisor.send(TestRequest("fail")) })
    }

    @Test
    fun `validation happens before handler`() {
        val validator = mockk<Validator>()
        every { validator.validate(any<Any>()) } returns emptySet()
        val handler = object : RequestHandler<TestRequest, String> {
            override fun exec(request: TestRequest): String = request.value
        }
        val supervisor = DefaultRequestSupervisor(listOf(handler), emptyList(), validator)

        assertEquals("valid", supervisor.send(TestRequest("valid")))
    }

    @Test
    fun `async fails only when reliable request capability is invoked`() {
        val handler = object : RequestHandler<TestRequest, String> {
            override fun exec(request: TestRequest): String = request.value
        }
        val supervisor = DefaultRequestSupervisor(listOf(handler), emptyList(), null)

        val exception = assertThrows<CapabilityUnavailableException> {
            supervisor.async(TestRequest("later"))
        }
        assertEquals("reliable-requests", exception.capability)
    }
}
