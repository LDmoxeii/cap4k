package com.only4.cap4k.ddd.core.application.query

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PageRequestTest {

    @Test
    fun `page request exposes only page coordinates`() {
        val request = object : PageRequest {
            override val pageNum: Int = 2
            override val pageSize: Int = 50
        }

        assertEquals(2, request.pageNum)
        assertEquals(50, request.pageSize)
    }
}
