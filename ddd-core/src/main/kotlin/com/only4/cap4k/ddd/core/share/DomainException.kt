package com.only4.cap4k.ddd.core.share

/**
 * 领域异常
 *
 * @author binking338
 * @date 2023/8/15
 */
class DomainException : RuntimeException {
    constructor(message: String) : super(message)

    constructor(message: String, innerException: Throwable) : super(message, innerException)
}
