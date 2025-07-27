package com.only4.cap4k.ddd.core.archinfo.model.elements.aggreagate

import com.only4.cap4k.ddd.core.archinfo.model.ClassRef
import com.only4.cap4k.ddd.core.archinfo.model.Element

/**
 * 聚合工厂
 *
 * @author LD_moxeii
 * @date 2025/07/27
 */
data class FactoryElement(
    override val classRef: String,
    val payloadClassRef: List<String> = emptyList(),
    override val name: String,
    override val description: String
) : Element, ClassRef {
    override val type: String = Element.TYPE_FACTORY
}
