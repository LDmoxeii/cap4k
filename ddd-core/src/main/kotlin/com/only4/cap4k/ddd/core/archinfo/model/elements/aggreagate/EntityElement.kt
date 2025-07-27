package com.only4.cap4k.ddd.core.archinfo.model.elements.aggreagate

import com.only4.cap4k.ddd.core.archinfo.model.ClassRef
import com.only4.cap4k.ddd.core.archinfo.model.Element

/**
 * 实体
 *
 * @author LD_moxeii
 * @date 2025/07/27
 */
data class EntityElement(
    override val classRef: String,
    override val name: String,
    override val description: String,
    val root: Boolean = false
) : Element, ClassRef {
    override val type: String = Element.TYPE_ENTITY
}
