package com.acme.demo.application.smoke

import com.acme.demo.application.commands.order.submit.SubmitOrderCmd

object CompileSmoke {
    val sample = SubmitOrderCmd.Request(orderId = 1L)
}
