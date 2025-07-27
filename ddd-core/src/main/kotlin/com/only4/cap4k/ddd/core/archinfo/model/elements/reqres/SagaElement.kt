package com.only4.cap4k.ddd.core.archinfo.model.elements.reqres

import com.only4.cap4k.ddd.core.archinfo.model.ClassRef
import com.only4.cap4k.ddd.core.archinfo.model.Element

/**
 * Saga
 *
 * @author LD_moxeii
 * @date 2025/07/27
 */
data class SagaElement(
    override val classRef: String,
    val requestClassRef: String,
    val responseClassRef: String,
    override val name: String,
    override val description: String
) : Element, ClassRef {
    override val type: String = Element.TYPE_SAGA
}
