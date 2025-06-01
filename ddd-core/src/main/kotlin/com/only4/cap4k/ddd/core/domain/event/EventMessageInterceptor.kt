package com.only4.cap4k.ddd.core.domain.event

import org.springframework.messaging.Message
import org.springframework.messaging.MessageHeaders
import java.util.*


interface EventMessageInterceptor {
    /**
     * 提交发布
     *
     * @param message
     * @return
     */
    fun initPublish(message: Message<*>)

    /**
     * 发布前
     *
     * @param message
     */
    fun prePublish(message: Message<*>)

    /**
     * 发布后
     *
     * @param message
     */
    fun postPublish(message: Message<*>)

    /**
     * 订阅处理前
     *
     * @param message
     * @return
     */
    fun preSubscribe(message: Message<*>)

    /**
     * 订阅处理后
     *
     * @param message
     * @return
     */
    fun postSubscribe(message: Message<*>)

    class ModifiableMessageHeaders : MessageHeaders {
        constructor(headers: Map<String, Any>) : this(
            headers,
            headers[ID]?.let { UUID.fromString(it.toString()) },
            headers[TIMESTAMP]?.toString()?.toLong()
        )

        constructor(
            headers: Map<String, Any>?,
            id: UUID?,
            timestamp: Long?
        ) : super(headers, id, timestamp)

        override fun putAll(map: Map<out String, *>) {
            rawHeaders.putAll(map)
        }

        override fun put(key: String, value: Any): Any {
            return rawHeaders.put(key, value)!!
        }

        override fun remove(key: String): Any {
            return rawHeaders.remove(key)!!
        }

        override fun clear() {
            rawHeaders.clear()
        }
    }

}


