package com.only4.cap4k.ddd.endpoint.rpc.http.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("cap4k.endpoint.rpc.http")
class EndpointRpcHttpProperties(
    var serviceId: String = "",
    var routes: MutableMap<String, String> = linkedMapOf(),
    var connectTimeout: Duration = Duration.ofSeconds(3),
    var responseTimeout: Duration = Duration.ofSeconds(10),
)
