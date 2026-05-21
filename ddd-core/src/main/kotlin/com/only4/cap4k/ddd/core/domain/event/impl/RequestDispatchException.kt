package com.only4.cap4k.ddd.core.domain.event.impl

class RequestDispatchException(
    val requestParamClass: Class<*>,
    val requestHandlerClass: Class<*>?,
    val diagnosticContext: EventDispatchDiagnostic?,
    cause: Throwable,
) : RuntimeException(
    buildMessage(requestParamClass, requestHandlerClass, cause, diagnosticContext),
    cause,
) {
    companion object {
        private fun buildMessage(
            requestParamClass: Class<*>,
            requestHandlerClass: Class<*>?,
            cause: Throwable,
            diagnosticContext: EventDispatchDiagnostic?,
        ): String = buildString {
            append("Request dispatch failed for ")
            append(requestParamClass.name)
            requestHandlerClass?.let {
                append("; handler: ")
                append(it.name)
            } ?: append("; handler: <unresolved>")
            cause.message?.let {
                append("; cause: ")
                append(it)
            }
            diagnosticContext?.listenerBeanName?.let {
                append("; listener: ")
                append(it)
            }
        }
    }
}
