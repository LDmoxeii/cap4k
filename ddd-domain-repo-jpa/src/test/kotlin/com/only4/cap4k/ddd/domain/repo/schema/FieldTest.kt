package com.only4.cap4k.ddd.domain.repo.schema

import io.mockk.every
import io.mockk.mockk
import jakarta.persistence.criteria.CriteriaBuilder
import org.hibernate.query.sqm.tree.domain.SqmBasicValuedSimplePath
import org.hibernate.spi.NavigablePath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class FieldTest {

    @Test
    fun `field created from name keeps printable name`() {
        val field = Field<Long>("id")

        assertEquals("id", field.toString())
        assertEquals(null, field.path())
    }

    @Test
    fun `field created from path keeps local name and supports nullable dsl skip`() {
        val path = mockk<SqmBasicValuedSimplePath<Long>>()
        val criteriaBuilder = mockk<CriteriaBuilder>(relaxed = true)
        val navigablePath = mockk<NavigablePath>()
        every { path.navigablePath } returns navigablePath
        every { navigablePath.localName } returns "id"
        val field = Field(path, criteriaBuilder)

        assertEquals("id", field.toString())
        assertSame(path, field.path())
        assertNull(field.`eq?`(null))
    }
    @Test
    fun `nullable DSL operations skip absent values and empty collections`() {
        val text = Field<String>("name")
        val number = Field<Int>("rank")

        assertNull(text.`eq?`(null))
        assertNull(text.`neq?`(null))
        assertNull(text.`like?`(null))
        assertNull(text.`notLike?`(null))
        assertNull(number.`gt?`(null))
        assertNull(number.`ge?`(null))
        assertNull(number.`lt?`(null))
        assertNull(number.`le?`(null))
        assertNull(text.`in?`(null))
        assertNull(text.`in?`(emptyList<Any>()))
        assertNull(text.`notIn?`(null))
        assertNull(text.`notIn?`(emptyList<Any>()))
    }
}
