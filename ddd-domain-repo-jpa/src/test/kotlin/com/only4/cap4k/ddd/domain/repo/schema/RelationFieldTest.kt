package com.only4.cap4k.ddd.domain.repo.schema

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Predicate
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class RelationFieldTest {

    @Test
    fun `collection relation field delegates emptiness predicates to criteria builder`() {
        val path = mockk<Expression<Collection<OwnedChild>>>()
        val criteriaBuilder = mockk<CriteriaBuilder>()
        val emptyPredicate = mockk<Predicate>()
        val notEmptyPredicate = mockk<Predicate>()
        every { criteriaBuilder.isEmpty(path) } returns emptyPredicate
        every { criteriaBuilder.isNotEmpty(path) } returns notEmptyPredicate

        val field = RelationCollectionField<OwnedChild>(path, criteriaBuilder)

        assertSame(emptyPredicate, field.isEmpty())
        assertSame(notEmptyPredicate, field.isNotEmpty())
        verify(exactly = 1) { criteriaBuilder.isEmpty(path) }
        verify(exactly = 1) { criteriaBuilder.isNotEmpty(path) }
    }

    @Test
    fun `optional relation field uses backing collection emptiness for nullability`() {
        val path = mockk<Expression<Collection<OwnedChild>>>()
        val criteriaBuilder = mockk<CriteriaBuilder>()
        val nullPredicate = mockk<Predicate>()
        val notNullPredicate = mockk<Predicate>()
        every { criteriaBuilder.isEmpty(path) } returns nullPredicate
        every { criteriaBuilder.isNotEmpty(path) } returns notNullPredicate

        val field = RelationOptionalField<OwnedChild>(path, criteriaBuilder)

        assertSame(nullPredicate, field.isNull())
        assertSame(notNullPredicate, field.isNotNull())
        verify(exactly = 1) { criteriaBuilder.isEmpty(path) }
        verify(exactly = 1) { criteriaBuilder.isNotEmpty(path) }
    }

    private class OwnedChild
}
