package com.only4.cap4k.ddd.core.share

/**
 * 领域异常
 *
 * @author LD_moxeii
 * @date 2025/07/21
 */
class DomainException : RuntimeException {
    constructor(message: String) : super(message)

    constructor(message: String, innerException: Throwable) : super(message, innerException)
}
