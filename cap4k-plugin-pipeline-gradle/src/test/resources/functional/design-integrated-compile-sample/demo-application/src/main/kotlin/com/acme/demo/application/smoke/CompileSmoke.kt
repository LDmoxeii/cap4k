package com.acme.demo.application.smoke

import com.acme.demo.application.distributed.clients.authorize.IssueTokenCli
import com.acme.demo.application.subscribers.domain.order.OrderCreatedDomainEventSubscriber
import com.acme.demo.application.queries.order.read.FindOrderQry

@Suppress("unused")
internal data class CompileSmoke(
    val orderId: String? = null,
) {
    val queryRequest = FindOrderQry.Request(orderId = 1L)
    val clientRequest = IssueTokenCli.Request(account = "demo-account")
    val subscriberType = OrderCreatedDomainEventSubscriber::class
}
