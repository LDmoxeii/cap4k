package com.only4.cap4k.ddd.core.annotation

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConceptAnnotationTest {

    @Test
    fun `building block annotation targets classes and is retained in bytecode`() {
        assertEquals(RetentionPolicy.CLASS, BuildingBlock::class.java.getAnnotation(Retention::class.java).value)
        assertArrayEquals(arrayOf(ElementType.TYPE), BuildingBlock::class.java.getAnnotation(Target::class.java).value)
    }

    @Test
    fun `aggregate element annotation targets classes and is retained in bytecode`() {
        assertEquals(RetentionPolicy.CLASS, AggregateElement::class.java.getAnnotation(Retention::class.java).value)
        assertArrayEquals(arrayOf(ElementType.TYPE), AggregateElement::class.java.getAnnotation(Target::class.java).value)
    }
}
