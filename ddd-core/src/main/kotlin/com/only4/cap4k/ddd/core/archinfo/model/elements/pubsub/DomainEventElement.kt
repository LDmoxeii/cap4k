package com.only4.cap4k.ddd.core.archinfo.model.elements.pubsub

import com.only4.cap4k.ddd.core.archinfo.model.ClassRef
import com.only4.cap4k.ddd.core.archinfo.model.Element

/**
 * 领域事件
 *
 * @author LD_moxeii
 * @date 2025/07/27
 */
data class DomainEventElement(
    override val classRef: String,
    override val name: String,
    override val description: String,
    val subscribersRef: List<String> = emptyList()
) : Element, ClassRef {
    override val type: String = Element.TYPE_DOMAIN_EVENT
}
