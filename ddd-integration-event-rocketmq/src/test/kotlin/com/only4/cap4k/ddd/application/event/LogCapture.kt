package com.only4.cap4k.ddd.application.event

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory

internal fun captureFormattedLogs(loggerType: Class<*>, action: () -> Unit): List<String> {
    val logger = LoggerFactory.getLogger(loggerType) as Logger
    val appender = ListAppender<ILoggingEvent>().apply { start() }
    logger.addAppender(appender)
    return try {
        action()
        appender.list.map { it.formattedMessage }
    } finally {
        logger.detachAppender(appender)
        appender.stop()
    }
}
