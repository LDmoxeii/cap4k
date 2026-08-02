package com.only4.cap4k.analysis.metadata

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

class AnalysisMetadataAnnotationTest {

    @Test
    fun `design block metadata uses class retention and class target`() {
        assertEquals(
            RetentionPolicy.CLASS,
            DesignBlockMetadata::class.java.getAnnotation(Retention::class.java).value,
        )
        assertArrayEquals(
            arrayOf(ElementType.TYPE),
            DesignBlockMetadata::class.java.getAnnotation(Target::class.java).value,
        )
    }

    @Test
    fun `aggregate element metadata uses class retention and class target`() {
        assertEquals(
            RetentionPolicy.CLASS,
            AggregateElementMetadata::class.java.getAnnotation(Retention::class.java).value,
        )
        assertArrayEquals(
            arrayOf(ElementType.TYPE),
            AggregateElementMetadata::class.java.getAnnotation(Target::class.java).value,
        )
    }

    @Test
    fun `analysis metadata has no runtime reflection surface`() {
        assertNull(RuntimeProbe::class.java.getAnnotation(DesignBlockMetadata::class.java))
        assertNull(RuntimeProbe::class.java.getAnnotation(AggregateElementMetadata::class.java))
    }

    @DesignBlockMetadata(tag = "query", name = "RuntimeProbe", family = "query")
    @AggregateElementMetadata(aggregate = "RuntimeProbe", type = "entity")
    private class RuntimeProbe
}
