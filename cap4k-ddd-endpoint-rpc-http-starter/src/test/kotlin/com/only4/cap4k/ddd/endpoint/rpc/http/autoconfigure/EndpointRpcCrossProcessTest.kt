package com.only4.cap4k.ddd.endpoint.rpc.http.autoconfigure

import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.capability.CapabilityCall
import com.only4.cap4k.ddd.core.application.capability.CapabilityHandler
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.ddd.endpoint.rpc.EndpointRpcProviderBinding
import com.only4.cap4k.ddd.endpoint.rpc.EndpointTransportInvoker
import com.only4.cap4k.ddd.endpoint.rpc.RemoteEndpointHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

class EndpointRpcCrossProcessTest {
    @Test
    fun `business direct Endpoint and Capability ACL use Mediator over a real Provider process`() {
        val port = ServerSocket(0).use { it.localPort }
        val provider = startProvider(port)
        try {
            awaitReady(provider)
            val consumer = SpringApplicationBuilder(ConsumerApplication::class.java)
                .web(WebApplicationType.NONE)
                .properties(
                    "spring.main.banner-mode=off",
                    "logging.level.root=ERROR",
                    "cap4k.endpoint.rpc.http.routes[booking-service]=http://127.0.0.1:$port",
                    "cap4k.endpoint.rpc.http.connect-timeout=2s",
                    "cap4k.endpoint.rpc.http.response-timeout=5s",
                )
                .run()
            try {
                val direct = Mediator.endpoints.send(BookingEndpoint.Request("customer-1"))
                assertEquals(BookingEndpoint.Response("booking-customer-1"), direct)
                assertEquals(
                    "booking-customer-2",
                    Mediator.capabilities.call(CreateBookingCapability("customer-2")),
                )
                assertTrue(consumer.getBeansOfType(EndpointRpcProviderBinding::class.java).isEmpty())
                assertTrue(consumer.containsBean(EndpointRpcHttpAutoConfiguration.RPC_PATH).not())
            } finally {
                consumer.close()
            }
        } finally {
            runCatching {
                provider.outputStream.bufferedWriter().use { it.newLine() }
            }
            if (!provider.waitFor(10, TimeUnit.SECONDS)) {
                provider.destroyForcibly()
            }
        }
    }

    private fun startProvider(port: Int): Process {
        val java = Path.of(
            System.getProperty("java.home"),
            "bin",
            if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java",
        )
        val classpath = requireNotNull(System.getProperty("cap4k.test.runtime.classpath")) {
            "cap4k.test.runtime.classpath is required for the Provider subprocess"
        }
        return ProcessBuilder(
            java.toString(),
            "-cp",
            classpath,
            EndpointRpcCrossProcessProvider::class.java.name,
            port.toString(),
        ).redirectErrorStream(true).start()
    }

    private fun awaitReady(provider: Process) {
        val reader = BufferedReader(InputStreamReader(provider.inputStream))
        val deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos()
        val output = StringBuilder()
        while (System.nanoTime() < deadline) {
            if (!provider.isAlive) {
                reader.lines().forEach { output.appendLine(it) }
                throw AssertionError("Provider process exited before readiness:\n$output")
            }
            if (reader.ready()) {
                val line = reader.readLine() ?: break
                output.appendLine(line)
                if (line == "CAP4K_ENDPOINT_RPC_PROVIDER_READY") return
            } else {
                Thread.sleep(25)
            }
        }
        throw AssertionError("Provider process did not become ready:\n$output")
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(ConsumerSupport::class)
    class ConsumerApplication

    @Configuration(proxyBeanMethods = false)
    class ConsumerSupport {
        @Bean
        fun remoteBookingHandler(invoker: EndpointTransportInvoker): EndpointHandler<BookingEndpoint.Request, BookingEndpoint.Response> =
            RemoteBookingEndpointHandler(invoker)

        @Bean
        fun createBookingCapabilityHandler(): CapabilityHandler<CreateBookingCapability, String> =
            CreateBookingCapabilityHandler()
    }
}

private class CreateBookingCapabilityHandler : CapabilityHandler<CreateBookingCapability, String> {
    override fun call(request: CreateBookingCapability): String =
        Mediator.endpoints.send(BookingEndpoint.Request(request.customerId)).bookingId
}
private class RemoteBookingEndpointHandler(
    invoker: EndpointTransportInvoker,
) : EndpointHandler<BookingEndpoint.Request, BookingEndpoint.Response> by RemoteEndpointHandler(
    "booking-service",
    BookingEndpoint.OPERATION_NAME,
    BookingEndpoint.Request::class,
    BookingEndpoint.Response::class,
    invoker,
)
object EndpointRpcCrossProcessProvider {
    @JvmStatic
    fun main(args: Array<String>) {
        val port = args.single().toInt()
        val context = SpringApplicationBuilder(ProviderApplication::class.java)
            .web(WebApplicationType.SERVLET)
            .properties(
                "server.port=$port",
                "spring.main.banner-mode=off",
                "logging.level.root=ERROR",
                "cap4k.endpoint.rpc.http.service-id=booking-service",
            )
            .run()
        try {
            println("CAP4K_ENDPOINT_RPC_PROVIDER_READY")
            System.out.flush()
            readLine()
        } finally {
            context.close()
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(ProviderSupport::class)
    class ProviderApplication

    @Configuration(proxyBeanMethods = false)
    class ProviderSupport {
        @Bean
        fun bookingBinding() = EndpointRpcProviderBinding(
            "booking-service",
            BookingEndpoint.OPERATION_NAME,
            BookingEndpoint.Request::class,
            BookingEndpoint.Response::class,
        )

        @Bean
        fun bookingHandler(): EndpointHandler<BookingEndpoint.Request, BookingEndpoint.Response> =
            ProviderBookingEndpointHandler()
    }
}

private class ProviderBookingEndpointHandler : EndpointHandler<BookingEndpoint.Request, BookingEndpoint.Response> {
    override fun handle(request: BookingEndpoint.Request): BookingEndpoint.Response =
        BookingEndpoint.Response("booking-${request.customerId}")
}
object BookingEndpoint {
    const val OPERATION_NAME = "booking.create"
    data class Request(val customerId: String) : EndpointRequest<Response>
    data class Response(val bookingId: String)
}

data class CreateBookingCapability(val customerId: String) : CapabilityCall<String>




