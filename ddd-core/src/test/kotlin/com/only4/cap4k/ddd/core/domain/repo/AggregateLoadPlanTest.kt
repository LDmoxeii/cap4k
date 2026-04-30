package com.only4.cap4k.ddd.core.domain.repo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class AggregateLoadPlanTest {

    @Test
    @DisplayName("aggregate load plan should expose explicit read intents only")
    fun `aggregate load plan should expose explicit read intents only`() {
        assertEquals(
            listOf(AggregateLoadPlan.MINIMAL, AggregateLoadPlan.WHOLE_AGGREGATE),
            AggregateLoadPlan.entries.toList()
        )
    }
}
