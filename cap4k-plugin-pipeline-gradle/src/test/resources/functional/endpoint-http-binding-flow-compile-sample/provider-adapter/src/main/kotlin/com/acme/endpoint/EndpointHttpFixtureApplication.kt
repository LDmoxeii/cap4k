package com.acme.endpoint

import jakarta.servlet.Filter
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean

@SpringBootApplication(scanBasePackages = ["com.acme.endpoint"])
class EndpointHttpFixtureApplication {
    @Bean
    fun endpointFixtureFilter(): Filter = Filter { request, response, chain ->
        (response as HttpServletResponse).setHeader("X-Endpoint-Filter", "applied")
        chain.doFilter(request, response)
    }
}
