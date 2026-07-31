package com.only4.cap4k.ddd.core.application.query

import jakarta.validation.ConstraintViolationException
import java.util.concurrent.CompletionStage

/**
 * Dispatches local queries without owning a write Unit of Work.
 */
interface QuerySupervisor {
    /**
     * @throws ConstraintViolationException when query validation fails
     */
    fun <QUERY : Query<RESULT>, RESULT : Any> ask(query: QUERY): RESULT

    /**
     * Schedules the same blocking query Handler through the bounded Query executor.
     * Every failure is reported by the returned stage.
     */
    fun <QUERY : Query<RESULT>, RESULT : Any> askAsync(query: QUERY): CompletionStage<RESULT>

    companion object {
        @JvmStatic
        val instance: QuerySupervisor
            get() = QuerySupervisorSupport.instance
    }
}
