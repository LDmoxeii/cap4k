package com.only4.cap4k.ddd.core.archinfo.model.elements.pubsub

import com.only4.cap4k.ddd.core.archinfo.model.ClassRef
import com.only4.cap4k.ddd.core.archinfo.model.Element

/**
 * 订阅
 *
 * @author LD_moxeii
 * @date 2025/07/27
 */
data class SubscriberElement(
    override val classRef: String,
    override val name: String,
    override val description: String,
    val eventRef: String
) : Element, ClassRef {
    override val type: String = Element.TYPE_SUBSCRIBER
}
