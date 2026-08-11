package com.only4.cap4k.ddd.core.share

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PageDataTest {
    @Test
    fun `transform preserves page coordinates and total count`() {
        val source = PageData.create(
            pageSize = 5,
            pageNum = 3,
            totalCount = 42,
            list = listOf(1, 2),
        )

        val transformed = source.transform { "item-$it" }

        assertEquals(3, transformed.pageNum)
        assertEquals(5, transformed.pageSize)
        assertEquals(42, transformed.totalCount)
        assertEquals(listOf("item-1", "item-2"), transformed.list)
        assertEquals(9, transformed.totalPages)
    }
}
