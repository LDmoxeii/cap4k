package com.acme.endpoint.contract

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import jakarta.validation.constraints.NotBlank

@DesignBlockMetadata(
    tag = "endpoint",
    name = "CreateBooking",
    packageName = "booking",
    operationName = "booking.create",
    family = "endpoint",
)
object CreateBookingEndpoint {
    const val OPERATION_NAME: String = "booking.create"

    data class Request(
        @field:NotBlank
        val customerName: String,
    ) : EndpointRequest<Response>

    data class Response(
        val bookingId: String,
    )
}

@DesignBlockMetadata(
    tag = "endpoint",
    name = "GetResource",
    packageName = "resource",
    operationName = "resource.get",
    family = "endpoint",
)
object GetResourceEndpoint {
    const val OPERATION_NAME: String = "resource.get"

    data class Request(
        val sourceName: String,
    ) : EndpointRequest<Response>

    data class Response(
        val url: String,
    )
}
