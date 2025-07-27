package com.only4.cap4k.ddd.core.archinfo.model

import com.only4.cap4k.ddd.core.archinfo.model.elements.ListCatalog
import com.only4.cap4k.ddd.core.archinfo.model.elements.MapCatalog

/**
 * 架构
 *
 * @author LD_moxeii
 * @date 2025/07/27
 */
data class Architecture(
    val application: Application,
    val domain: Domain
) {
    data class Application(
        val requests: MapCatalog,
        val events: MapCatalog
    )

    data class Domain(
        val aggregates: MapCatalog,
        val services: ListCatalog
    )
}
