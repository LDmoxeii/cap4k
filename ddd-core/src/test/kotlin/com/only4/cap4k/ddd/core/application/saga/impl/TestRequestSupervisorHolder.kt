package com.only4.cap4k.ddd.core.application.saga.impl

import com.only4.cap4k.ddd.core.application.RequestSupervisor
import io.mockk.mockk

internal object TestRequestSupervisorHolder {
    val instance: RequestSupervisor = mockk()
}
