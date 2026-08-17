package demo.consumerstart

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.capability.CapabilityHandler
import com.only4.cap4k.ddd.endpoint.rpc.EndpointRpcProviderBinding
import com.only4.cap4k.ddd.endpoint.rpc.http.EndpointRpcHttpRequestCustomizer
import demo.consumer.BookingConsumer
import demo.consumer.CreateBookingCapability
import demo.consumer.CreateBookingCapabilityHandler
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(ConsumerHandlers::class)
class ConsumerApplication

@Configuration(proxyBeanMethods = false)
class ConsumerHandlers {
    @Bean
    fun createBookingCapabilityHandler(): CapabilityHandler<CreateBookingCapability, String> =
        CreateBookingCapabilityHandler()

    @Bean
    fun endpointRpcHttpRequestCustomizer(): EndpointRpcHttpRequestCustomizer =
        EndpointRpcHttpRequestCustomizer { _, _, headers -> headers["X-Rpc-Test-Auth"] = "fixture-secret" }
}

fun main(args: Array<String>) {
    val providerBaseUri = args.single()
    val context = SpringApplicationBuilder(ConsumerApplication::class.java)
        .web(WebApplicationType.NONE)
        .properties(
            "spring.main.banner-mode=off",
            "logging.level.root=ERROR",
            "cap4k.endpoint.rpc.http.routes[booking-service]=$providerBaseUri",
            "cap4k.endpoint.rpc.http.connect-timeout=2s",
            "cap4k.endpoint.rpc.http.response-timeout=5s",
        )
        .run()
    try {
        check(BookingConsumer().create("direct") == "booking-direct")
        check(Mediator.capabilities.call(CreateBookingCapability("capability")) == "booking-capability")
        check(context.getBeansOfType(EndpointRpcProviderBinding::class.java).isEmpty())
        println("CAP4K_GENERATED_RPC_CONSUMER_SUCCESS")
    } finally {
        context.close()
    }
}

