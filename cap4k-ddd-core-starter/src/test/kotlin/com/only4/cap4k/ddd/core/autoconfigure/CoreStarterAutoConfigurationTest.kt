package com.only4.cap4k.ddd.core.autoconfigure

import com.only4.cap4k.ddd.core.CapabilityUnavailableException
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.RequestHandler
import com.only4.cap4k.ddd.core.application.RequestParam
import com.only4.cap4k.ddd.core.application.RequestRecordRepository
import com.only4.cap4k.ddd.core.application.RequestSupervisor
import com.only4.cap4k.ddd.core.domain.event.DomainEventSupervisor
import com.only4.cap4k.ddd.core.domain.event.EventSubscriberManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.event.EventListener

class CoreStarterAutoConfigurationTest {
    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                CoreIdAutoConfiguration::class.java,
                CoreRuntimeAutoConfiguration::class.java,
                CoreDomainEventAutoConfiguration::class.java,
            )
        )
        .withBean(TestRequestHandler::class.java)
        .withBean(TestEventListener::class.java)

    @Test
    fun `core starter provides synchronous request uuid7 ioc and local event without reliable stores`() {
        contextRunner.run { context ->
            assertTrue(context.startupFailure == null)
            assertTrue(context.getBeansOfType(RequestRecordRepository::class.java).isEmpty())
            assertEquals(1, context.getBeansOfType(EventSubscriberManager::class.java).size)
            assertEquals("handled:ok", Mediator.commands.send(TestRequest("ok")))
            assertTrue(Mediator.identifiers.next("uuid7", String::class).isNotBlank())
            assertEquals(context, Mediator.ioc)

            val listener = context.getBean(TestEventListener::class.java)
            val entity = Any()
            val event = TestEvent("created")
            DomainEventSupervisor.instance.attach(event, entity)
            DomainEventSupervisor.manager.release(setOf(entity))
            assertEquals(listOf(event), listener.events)

            val exception = assertThrows<CapabilityUnavailableException> {
                Mediator.commands.async(TestRequest("later"))
            }
            assertEquals("reliable-requests", exception.capability)
        }
    }

    @Test
    fun `core starter fails startup when a capability has multiple providers`() {
        ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    CoreIdAutoConfiguration::class.java,
                    CoreRuntimeAutoConfiguration::class.java,
                    CoreDomainEventAutoConfiguration::class.java,
                )
            )
            .withBean("requestA", ConflictingRequestSupervisor::class.java)
            .withBean("requestB", ConflictingRequestSupervisor::class.java)
            .run { context ->
                val failure = requireNotNull(context.startupFailure).stackTraceToString()
                assertTrue(failure.contains("cap4k capability 'requests' requires exactly one provider"))
                assertTrue(failure.contains("requestA"))
                assertTrue(failure.contains("requestB"))
            }
    }

    data class TestRequest(val value: String) : RequestParam<String>

    class TestRequestHandler : RequestHandler<TestRequest, String> {
        override fun exec(request: TestRequest): String = "handled:${request.value}"
    }

    class ConflictingRequestSupervisor : RequestSupervisor {
        override fun <REQUEST : RequestParam<RESPONSE>, RESPONSE : Any> send(request: REQUEST): RESPONSE =
            error("not invoked")
    }

    data class TestEvent(val value: String)

    class TestEventListener {
        val events = mutableListOf<TestEvent>()

        @EventListener
        fun on(event: TestEvent) {
            events += event
        }
    }
}
