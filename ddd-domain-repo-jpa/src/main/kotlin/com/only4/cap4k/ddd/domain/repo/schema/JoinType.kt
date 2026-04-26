package com.only4.cap4k.ddd.domain.repo.schema

enum class JoinType {
    INNER,
    LEFT,
    RIGHT,
    ;

    fun toJpaJoinType(): jakarta.persistence.criteria.JoinType = when (this) {
        INNER -> jakarta.persistence.criteria.JoinType.INNER
        LEFT -> jakarta.persistence.criteria.JoinType.LEFT
        RIGHT -> jakarta.persistence.criteria.JoinType.RIGHT
    }
}
