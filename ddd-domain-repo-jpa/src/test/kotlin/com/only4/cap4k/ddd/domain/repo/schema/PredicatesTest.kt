package com.only4.cap4k.ddd.domain.repo.schema

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Predicate
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class PredicatesTest {
    private interface PredicateWithCriteriaBuilder : Predicate {
        fun criteriaBuilder(): CriteriaBuilder
    }

    @Test
    fun `and collapses empty null and single predicate`() {
        val predicate = mockk<Predicate>()

        assertNull(and())
        assertNull(and(null, null))
        assertSame(predicate, and(null, predicate))
    }

    @Test
    fun `or collapses empty null and single predicate`() {
        val predicate = mockk<Predicate>()

        assertNull(or())
        assertNull(or(null, null))
        assertSame(predicate, or(null, predicate))
    }

    @Test
    fun `nested and-or composition delegates to the originating criteria builder`() {
        val criteriaBuilder = mockk<CriteriaBuilder>()
        val first = predicate(criteriaBuilder)
        val second = predicate(criteriaBuilder)
        val third = predicate(criteriaBuilder)
        val disjunction = predicate(criteriaBuilder)
        val nested = mockk<Predicate>()
        every { criteriaBuilder.or(first, second) } returns disjunction
        every { criteriaBuilder.and(disjunction, third) } returns nested

        val result = and(or(first, second), null, third)

        assertSame(nested, result)
        verify(exactly = 1) { criteriaBuilder.or(first, second) }
        verify(exactly = 1) { criteriaBuilder.and(disjunction, third) }
    }

    private fun predicate(criteriaBuilder: CriteriaBuilder): PredicateWithCriteriaBuilder =
        mockk<PredicateWithCriteriaBuilder>().also {
            every { it.criteriaBuilder() } returns criteriaBuilder
        }
}
