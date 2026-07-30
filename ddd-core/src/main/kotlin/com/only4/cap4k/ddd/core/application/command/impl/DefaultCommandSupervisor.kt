package com.only4.cap4k.ddd.core.application.command.impl

import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.ddd.core.application.command.CommandInterceptor
import com.only4.cap4k.ddd.core.application.command.CommandSupervisor
import com.only4.cap4k.ddd.core.application.UnitOfWork
import com.only4.cap4k.ddd.core.application.impl.SynchronousApplicationDispatcher
import com.only4.cap4k.ddd.core.domain.event.impl.EventRuntimeContext
import jakarta.validation.Validator

/**
 * Current-thread command dispatcher. Every Command enters the provider-owned
 * REQUIRED Unit of Work; nested Commands reuse the active context.
 */
open class DefaultCommandSupervisor(
    handlers: List<CommandHandler<*, *>>,
    interceptors: List<CommandInterceptor<*, *>>,
    validator: Validator?,
    private val unitOfWorkProvider: () -> UnitOfWork,
) : CommandSupervisor {
    private val dispatcher = SynchronousApplicationDispatcher(
        category = "command",
        handlers = handlers,
        handlerContract = CommandHandler::class.java,
        interceptors = interceptors,
        interceptorContract = CommandInterceptor::class.java,
        validator = validator,
        invokeHandler = { handler, message ->
            @Suppress("UNCHECKED_CAST")
            (handler as CommandHandler<Command<Any>, Any>).handle(message as Command<Any>)
        },
        beforeInvocation = { interceptor, message ->
            @Suppress("UNCHECKED_CAST")
            (interceptor as CommandInterceptor<Command<Any>, Any>).beforeCommand(message as Command<Any>)
        },
        afterInvocation = { interceptor, message, result ->
            @Suppress("UNCHECKED_CAST")
            (interceptor as CommandInterceptor<Command<Any>, Any>).afterCommand(
                message as Command<Any>,
                result,
            )
        },
    )

    fun init() = dispatcher.init()

    override fun <COMMAND : Command<RESULT>, RESULT : Any> send(command: COMMAND): RESULT =
        EventRuntimeContext.withCausalFrame("Command:${command.javaClass.name}") {
            unitOfWorkProvider().execute { dispatcher.dispatch(command) }
        }
}
