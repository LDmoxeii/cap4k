package com.only4.cap4k.ddd.core.domain.aggregate.impl

import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory
import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FactoryDerivedAggregateRootCatalogTest {
    @Test
    fun `catalog derives root type from factory and accepts provider proxy subclass`() {
        val catalog = FactoryDerivedAggregateRootCatalog(listOf(TestRootFactory()))

        assertTrue(catalog.isAggregateRoot(TestRoot::class.java))
        assertTrue(catalog.isAggregateRoot(TestRootProxy::class.java))
        assertFalse(catalog.isAggregateRoot(TestChild::class.java))
    }

    private open class TestRoot
    private class TestRootProxy : TestRoot()
    private class TestChild
    private data object Payload : AggregatePayload<TestRoot>

    private class TestRootFactory : AggregateFactory<Payload, TestRoot> {
        override fun create(entityPayload: Payload): TestRoot = TestRoot()
    }
}
