package com.only4.cap4k.ddd.core.archinfo.model.elements

import com.only4.cap4k.ddd.core.archinfo.model.Element

/**
 * 目录
 *
 * @author LD_moxeii
 * @date 2025/07/27
 */
class MapCatalog(
    override val name: String,
    override val description: String,
    elements: Map<String, Element> = emptyMap()
) : HashMap<String, Element>(elements), Element {

    constructor(
        name: String,
        description: String,
        elements: Collection<Element>
    ) : this(name, description, elements.associateBy { it.name })

    override val type: String
        get() = Element.TYPE_CATALOG
}
