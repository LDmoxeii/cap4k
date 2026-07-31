package com.only4.cap4k.ddd.core.application

/**
 * Internal bridge between aggregate factories/repositories and the active
 * persistence runtime. Existing managed aggregates are discovered through
 * dirty checking and therefore have no persistence intent operation.
 */
interface AggregatePersistenceIntentRecorder {
    fun registerNew(root: Any)

    fun registerDelete(root: Any)
}
