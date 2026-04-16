package com.acme.demo.application.smoke

import com.acme.demo.application.distributed.clients.authorize.IssueTokenCli
import com.acme.demo.application.order.events.OrderCreatedDomainEventSubscriber
import com.acme.demo.application.queries.order.read.FindOrderQry
import com.acme.demo.application.validators.order.OrderIdValid

@Suppress("unused")
internal data class CompileSmoke(
    @field:OrderIdValid
    val orderId: String? = null,
) {
    val queryRequest = FindOrderQry.Request(orderId = 1L)
    val clientRequest = IssueTokenCli.Request(account = "demo-account")
    val subscriberType = OrderCreatedDomainEventSubscriber::class
}
