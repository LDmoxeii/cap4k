package demo.providerstart

import com.acme.rpc.endpoint.rpc.generated.EndpointRpcProviderBindings
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import demo.provider.CreateBookingEndpointHandler
import jakarta.servlet.Filter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(EndpointRpcProviderBindings::class, ProviderHandlers::class)
class ProviderApplication

@Configuration(proxyBeanMethods = false)
class ProviderHandlers {
    @Bean
    fun createBookingEndpointHandler(): EndpointHandler<*, *> = CreateBookingEndpointHandler()

    @Bean
    fun rpcAuthenticationFilter(): Filter = Filter { request, response, chain ->
        val httpRequest = request as HttpServletRequest
        if (httpRequest.requestURI == "/cap4k/endpoints/rpc" && httpRequest.getHeader("X-Rpc-Test-Auth") != "fixture-secret") {
            (response as HttpServletResponse).sendError(401)
        } else {
            chain.doFilter(request, response)
        }
    }
}

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
        println("CAP4K_GENERATED_RPC_PROVIDER_READY")
        System.out.flush()
        readLine()
    } finally {
        context.close()
    }
}

