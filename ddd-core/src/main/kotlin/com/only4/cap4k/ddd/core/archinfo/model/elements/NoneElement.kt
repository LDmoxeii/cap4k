package com.only4.cap4k.ddd.core.archinfo.model.elements

import com.only4.cap4k.ddd.core.archinfo.model.Element

/**
 * 空元素
 *
 * @author LD_moxeii
 * @date 2025/07/27
 */
object NoneElement : Element {
    override val type: String = Element.TYPE_NONE
    override val name: String = "None"
    override val description: String = ""
}
