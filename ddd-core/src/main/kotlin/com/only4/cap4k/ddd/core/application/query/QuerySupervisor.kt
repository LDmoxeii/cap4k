package com.only4.cap4k.ddd.core.application.query

import jakarta.validation.ConstraintViolationException

/**
 * Synchronously dispatches local queries without owning a write Unit of Work.
 */
interface QuerySupervisor {
    /**
     * @throws ConstraintViolationException when query validation fails
     */
    fun <QUERY : Query<RESULT>, RESULT : Any> ask(query: QUERY): RESULT

    companion object {
        @JvmStatic
        val instance: QuerySupervisor
            get() = QuerySupervisorSupport.instance
    }
}
