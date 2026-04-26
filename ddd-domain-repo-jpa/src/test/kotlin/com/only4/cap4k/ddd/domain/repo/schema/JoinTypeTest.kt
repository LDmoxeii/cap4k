package com.only4.cap4k.ddd.domain.repo.schema

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JoinTypeTest {

    @Test
    fun `join type maps to jakarta join type`() {
        assertEquals(jakarta.persistence.criteria.JoinType.INNER, JoinType.INNER.toJpaJoinType())
        assertEquals(jakarta.persistence.criteria.JoinType.LEFT, JoinType.LEFT.toJpaJoinType())
        assertEquals(jakarta.persistence.criteria.JoinType.RIGHT, JoinType.RIGHT.toJpaJoinType())
    }
}
