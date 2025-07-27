package com.only4.cap4k.ddd.core.archinfo.model.elements

import com.only4.cap4k.ddd.core.archinfo.model.Element

/**
 * 目录
 *
 * @author LD_moxeii
 * @date 2025/07/27
 */
class ListCatalog(
    override val name: String,
    override val description: String,
    elements: Collection<Element> = emptyList()
) : ArrayList<Element>(elements), Element {

    override val type: String
        get() = Element.TYPE_CATALOG
}
