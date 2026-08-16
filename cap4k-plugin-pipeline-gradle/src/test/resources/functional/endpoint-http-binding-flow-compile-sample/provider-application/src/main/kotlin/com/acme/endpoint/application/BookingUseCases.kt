package com.acme.endpoint.application

import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.ddd.core.application.query.Query
import com.only4.cap4k.ddd.core.application.query.QueryHandler
import org.springframework.stereotype.Component

data class CreateBookingCommand(
    val customerName: String,
) : Command<CreateBookingResult>

data class CreateBookingResult(
    val bookingId: String,
)

@Component
class CreateBookingCommandHandler : CommandHandler<CreateBookingCommand, CreateBookingResult> {
    override fun handle(command: CreateBookingCommand): CreateBookingResult =
        CreateBookingResult(bookingId = "booking-${command.customerName.lowercase()}")
}

data class GetResourceQuery(
    val sourceName: String,
) : Query<GetResourceResult>

data class GetResourceResult(
    val url: String,
)

@Component
class GetResourceQueryHandler : QueryHandler<GetResourceQuery, GetResourceResult> {
    override fun handle(query: GetResourceQuery): GetResourceResult =
        GetResourceResult(url = "https://cdn.example.test/${query.sourceName}")
}
