package com.only4.cap4k.ddd.core.application.command

/**
 * A local application intent that may change state.
 *
 * Transaction and Unit of Work coordination are owned by [CommandSupervisor].
 */
interface Command<RESULT : Any>
