package com.only4.cap4k.ddd.core.domain.repo

/**
 * Describes how much of an aggregate graph a repository read should prepare.
 */
enum class AggregateLoadPlan {
    /**
     * Existing repository behavior.
     */
    DEFAULT,

    /**
     * Root-only read intent. Implementations must not force owned collections to load.
     */
    MINIMAL,

    /**
     * Command mutation intent. Implementations should prepare owned aggregate entities.
     */
    WHOLE_AGGREGATE,
}
