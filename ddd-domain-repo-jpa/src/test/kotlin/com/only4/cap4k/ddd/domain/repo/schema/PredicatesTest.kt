package com.only4.cap4k.ddd.domain.repo.schema

import io.mockk.mockk
import jakarta.persistence.criteria.Predicate
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class PredicatesTest {

    @Test
    fun `and collapses null and single predicate`() {
        val predicate = mockk<Predicate>()

        val result = and(null, predicate)

        assertSame(predicate, result)
    }

    @Test
    fun `or collapses null and single predicate`() {
        val predicate = mockk<Predicate>()

        val result = or(null, predicate)

        assertSame(predicate, result)
    }
}
