package com.only4.cap4k.ddd.core.archinfo.model.elements

import com.only4.cap4k.ddd.core.archinfo.model.ClassRef
import com.only4.cap4k.ddd.core.archinfo.model.Element

/**
 * 领域服务
 *
 * @author LD_moxeii
 * @date 2025/07/27
 */
data class DomainServiceElement(
    override val classRef: String,
    override val name: String,
    override val description: String
) : Element, ClassRef {
    override val type: String = Element.TYPE_DOMAIN_SERVICE
}
