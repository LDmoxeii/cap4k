package demo.consumer

import com.acme.rpc.contract.endpoints.booking.CreateBookingEndpoint
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.capability.CapabilityCall
import com.only4.cap4k.ddd.core.application.capability.CapabilityHandler

class BookingConsumer {
    fun create(customerId: String): String =
        Mediator.endpoints.send(CreateBookingEndpoint.Request(customerId)).bookingId
}

data class CreateBookingCapability(val customerId: String) : CapabilityCall<String>

class CreateBookingCapabilityHandler(
    private val bookingConsumer: BookingConsumer = BookingConsumer(),
) : CapabilityHandler<CreateBookingCapability, String> {
    override fun call(request: CreateBookingCapability): String = bookingConsumer.create(request.customerId)
}
