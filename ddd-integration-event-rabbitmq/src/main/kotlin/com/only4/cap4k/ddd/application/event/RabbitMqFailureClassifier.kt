package com.only4.cap4k.ddd.application.event

import org.springframework.amqp.AmqpConnectException
import java.io.EOFException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

internal object RabbitMqFailureClassifier {
    fun isTemporaryUnavailability(failure: Throwable): Boolean = generateSequence(failure) { it.cause }
        .any { cause ->
            cause is AmqpConnectException ||
                cause is ConnectException ||
                cause is NoRouteToHostException ||
                cause is SocketException ||
                cause is SocketTimeoutException ||
                cause is UnknownHostException ||
                cause is EOFException ||
                cause is TimeoutException
        }
}
