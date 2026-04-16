package com.acme.demo.application.smoke

import com.acme.demo.application.validators.order.OrderIdValid

data class CompileSmoke(
    @field:OrderIdValid
    val orderId: String? = null,
)
