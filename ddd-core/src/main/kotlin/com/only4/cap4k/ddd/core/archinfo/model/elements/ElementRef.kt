package com.only4.cap4k.ddd.core.archinfo.model.elements

import com.only4.cap4k.ddd.core.archinfo.model.Element

/**
 * 元素引用
 *
 * @author LD_moxeii
 * @date 2025/07/27
 */
data class ElementRef(
    val ref: String,
    override val name: String,
    override val description: String
) : Element {
    override val type: String = Element.TYPE_REF
}
